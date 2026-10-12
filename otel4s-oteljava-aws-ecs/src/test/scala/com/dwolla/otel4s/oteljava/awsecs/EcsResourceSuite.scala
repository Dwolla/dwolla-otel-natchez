package com.dwolla.otel4s.oteljava.awsecs

import cats.effect.*
import cats.effect.std.Env
import cats.effect.testkit.TestControl
import cats.syntax.all.*
import io.opentelemetry.api.common.AttributeKey.{stringArrayKey, stringKey}
import io.opentelemetry.sdk.resources.Resource as OTResource
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.testing.TestingLoggerFactory

import scala.jdk.CollectionConverters.*

class EcsResourceSuite extends CatsEffectSuite {
  import EcsMetadataFixtures.*

  private val metadataUri = uri"http://169.254.170.2/v4/container-id"

  private def metadataServer(container: String, task: String): Client[IO] =
    Client.fromHttpApp(HttpRoutes.of[IO] {
      case GET -> Root / "v4" / "container-id" => Ok(container)
      case GET -> Root / "v4" / "container-id" / "task" => Ok(task)
    }.orNotFound)

  private val loggerName = "com.dwolla.otel4s.oteljava.awsecs.EcsResource"

  private def failureMessage(uri: Uri): String =
    s"ECS resource detection from $uri failed; continuing without ECS resource attributes"

  private def withLogs[A](run: LoggerFactory[IO] => IO[A]): IO[(A, Vector[TestingLoggerFactory.LogMessage])] = {
    val factory = TestingLoggerFactory.atomic[IO]()
    run(factory).flatMap(a => factory.logged.map(a -> _))
  }

  private def assertOneDetectionWarning(logged: Vector[TestingLoggerFactory.LogMessage]): Unit =
    logged match {
      case Vector(TestingLoggerFactory.Warn(name, message, throwable, _)) =>
        assertEquals(name, loggerName)
        assertEquals(message, failureMessage(metadataUri))
        assert(throwable.isDefined, "the WARN entry should carry the throwable")
      case other => fail(s"expected exactly one WARN entry, got $other")
    }

  private def fromMetadataUri(client: Client[IO]): IO[OTResource] =
    EcsResource.fromMetadataUri(client, metadataUri)(implicitly, org.typelevel.log4cats.noop.NoOpFactory[IO])

  private def attribute(resource: OTResource, key: String): Option[String] = Option(resource.getAttribute(stringKey(key)))
  private def listAttribute(resource: OTResource, key: String): Option[List[String]] =
    Option(resource.getAttribute(stringArrayKey(key))).map(_.asScala.toList)

  test("an EC2 response without AvailabilityZone and with a short cluster name gives the attributes that are present") {
    fromMetadataUri(metadataServer(ec2Container, ec2Task)).map { resource =>
      assertEquals(attribute(resource, "cloud.provider"), "aws".some)
      assertEquals(attribute(resource, "cloud.platform"), "aws_ecs".some)
      assertEquals(attribute(resource, "cloud.region"), "us-west-2".some)
      assertEquals(attribute(resource, "cloud.account.id"), "111122223333".some)
      assertEquals(attribute(resource, "cloud.availability_zone"), None)
      assertEquals(attribute(resource, "aws.ecs.task.arn"), "arn:aws:ecs:us-west-2:111122223333:task/default/158d1c8083dd49d6b527399fd6414f5c".some)
      assertEquals(attribute(resource, "aws.ecs.task.id"), "158d1c8083dd49d6b527399fd6414f5c".some)
      assertEquals(attribute(resource, "aws.ecs.task.family"), "curltest".some)
      assertEquals(attribute(resource, "aws.ecs.task.revision"), "26".some)
      assertEquals(attribute(resource, "aws.ecs.launchtype"), "ec2".some)
      assertEquals(attribute(resource, "aws.ecs.cluster.arn"), "arn:aws:ecs:us-west-2:111122223333:cluster/default".some)
      assertEquals(attribute(resource, "aws.ecs.container.arn"), "arn:aws:ecs:us-west-2:111122223333:container/0206b271-b33f-47ab-86c6-a0ba208a70a9".some)
      assertEquals(attribute(resource, "cloud.resource_id"), "arn:aws:ecs:us-west-2:111122223333:container/0206b271-b33f-47ab-86c6-a0ba208a70a9".some)
      assertEquals(attribute(resource, "container.id"), "ea32192c8553fbff06c9340478a2ff089b2bb5646fb718b4ee206641c9086d66".some)
      assertEquals(attribute(resource, "container.name"), "ecs-curltest-24-curl-cca48e8dcadd97805600".some)
      assertEquals(attribute(resource, "container.image.name"), "111122223333.dkr.ecr.us-west-2.amazonaws.com/curltest".some)
      assertEquals(listAttribute(resource, "container.image.tags"), List("latest").some)
      assertEquals(listAttribute(resource, "aws.log.group.names"), List("/ecs/metadata").some)
      assertEquals(listAttribute(resource, "aws.log.stream.names"), List("ecs/curl/8f03e41243824aea923aca126495f665").some)
    }
  }

  test("a Fargate response keeps its cluster ARN and availability zone") {
    fromMetadataUri(metadataServer(fargateContainer, fargateTask)).map { resource =>
      assertEquals(attribute(resource, "aws.ecs.cluster.arn"), "arn:aws:ecs:us-west-2:111122223333:cluster/default".some)
      assertEquals(attribute(resource, "cloud.availability_zone"), "us-west-2d".some)
      assertEquals(attribute(resource, "aws.ecs.launchtype"), "fargate".some)
      assertEquals(listAttribute(resource, "container.image.tags"), List("1.2.3").some)
    }
  }

  test("non-awslogs log drivers add no aws.log attributes") {
    fromMetadataUri(metadataServer(fargateContainer, fargateTask)).map { resource =>
      assertEquals(listAttribute(resource, "aws.log.group.names"), None)
      assertEquals(listAttribute(resource, "aws.log.stream.names"), None)
    }
  }

  test("an old agent's response without LaunchType, logs, or ContainerARN still gives the task attributes") {
    fromMetadataUri(metadataServer(minimalContainer, minimalTask)).map { resource =>
      assertEquals(attribute(resource, "aws.ecs.task.id"), "0123".some)
      assertEquals(attribute(resource, "aws.ecs.launchtype"), None)
      assertEquals(attribute(resource, "aws.ecs.container.arn"), None)
    }
  }

  test("an HTTP error gives the empty resource and logs one WARN") {
    val failing = Client.fromHttpApp(HttpApp[IO](_ => InternalServerError()))
    withLogs(implicit lf => EcsResource.fromMetadataUri(failing, metadataUri)).map { case (resource, logged) =>
      assertEquals(resource, OTResource.empty())
      assertOneDetectionWarning(logged)
    }
  }

  test("an undecodable response gives the empty resource and logs one WARN") {
    withLogs(implicit lf => EcsResource.fromMetadataUri(metadataServer("""{"unexpected": true}""", ec2Task), metadataUri)).map { case (resource, logged) =>
      assertEquals(resource, OTResource.empty())
      assertOneDetectionWarning(logged)
    }
  }

  test("a hanging endpoint gives the empty resource after the timeout and logs one WARN") {
    val hanging = Client.fromHttpApp(HttpApp[IO](_ => IO.never))
    TestControl.executeEmbed(withLogs(implicit lf => EcsResource.fromMetadataUri(hanging, metadataUri).timed)).map { case ((elapsed, resource), logged) =>
      assertEquals(resource, OTResource.empty())
      assertEquals(elapsed, EcsResource.timeout)
      assertOneDetectionWarning(logged)
    }
  }

  test("an image reference with a digest and no tag reports no container.image.tags") {
    val digestImage = "111122223333.dkr.ecr.us-west-2.amazonaws.com/curltest@sha256:d691691e9652791a60114e67b365688d20d19940dde7c4736ea30e660d8d3553"
    val container = ec2Container.replace("111122223333.dkr.ecr.us-west-2.amazonaws.com/curltest:latest", digestImage)
    assert(container.contains(digestImage))
    fromMetadataUri(metadataServer(container, ec2Task)).map { resource =>
      assertEquals(attribute(resource, "container.image.name"), "111122223333.dkr.ecr.us-west-2.amazonaws.com/curltest".some)
      assertEquals(listAttribute(resource, "container.image.tags"), None)
    }
  }

  test("an image reference with a sha512 digest and no tag keeps the repository name and reports no container.image.tags") {
    val digestImage = s"111122223333.dkr.ecr.us-west-2.amazonaws.com/curltest@sha512:${"ab12" * 32}"
    val container = ec2Container.replace("111122223333.dkr.ecr.us-west-2.amazonaws.com/curltest:latest", digestImage)
    assert(container.contains(digestImage))
    fromMetadataUri(metadataServer(container, ec2Task)).map { resource =>
      assertEquals(attribute(resource, "container.image.name"), "111122223333.dkr.ecr.us-west-2.amazonaws.com/curltest".some)
      assertEquals(listAttribute(resource, "container.image.tags"), None)
    }
  }

  private def envWith(vars: Map[String, String]): Env[IO] = new Env[IO] {
    def get(name: String): IO[Option[String]] = vars.get(name).pure[IO]
    def entries: IO[scala.collection.immutable.Iterable[(String, String)]] = vars.toList.pure[IO]
  }

  private val unreachable: Client[IO] =
    Client.fromHttpApp(HttpApp[IO](req => IO.raiseError(new AssertionError(s"unexpected request $req"))))

  test("without ECS_CONTAINER_METADATA_URI_V4, detection gives the empty resource without calling the client") {
    withLogs(implicit lf => EcsResource.detect(unreachable)(implicitly, envWith(Map.empty), lf)).map { case (resource, logged) =>
      assertEquals(resource, OTResource.empty())
      assertEquals(logged, Vector.empty)
    }
  }

  test("with ECS_CONTAINER_METADATA_URI_V4 set, detection reads the metadata endpoint") {
    withLogs(implicit lf => EcsResource.detect(metadataServer(ec2Container, ec2Task))(implicitly, envWith(Map("ECS_CONTAINER_METADATA_URI_V4" -> metadataUri.renderString)), lf)).map { case (resource, logged) =>
      assertEquals(attribute(resource, "aws.ecs.task.id"), "158d1c8083dd49d6b527399fd6414f5c".some)
      assertEquals(logged, Vector.empty)
    }
  }

  test("a malformed ECS_CONTAINER_METADATA_URI_V4 gives the empty resource without calling the client") {
    val malformed = "http://[invalid"
    assert(Uri.fromString(malformed).isLeft)
    withLogs(implicit lf => EcsResource.detect(unreachable)(implicitly, envWith(Map("ECS_CONTAINER_METADATA_URI_V4" -> malformed)), lf)).map { case (resource, logged) =>
      assertEquals(resource, OTResource.empty())
      assertEquals(logged, Vector.empty)
    }
  }
}
