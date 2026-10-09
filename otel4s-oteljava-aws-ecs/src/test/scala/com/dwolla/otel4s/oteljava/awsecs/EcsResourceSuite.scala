package com.dwolla.otel4s.oteljava.awsecs

import cats.effect.*
import cats.effect.testkit.TestControl
import cats.syntax.all.*
import io.opentelemetry.api.common.AttributeKey.{stringArrayKey, stringKey}
import io.opentelemetry.sdk.resources.Resource as OTResource
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.implicits.*

import scala.jdk.CollectionConverters.*

class EcsResourceSuite extends CatsEffectSuite {
  import EcsMetadataFixtures.*

  private val metadataUri = uri"http://169.254.170.2/v4/container-id"

  private def metadataServer(container: String, task: String): Client[IO] =
    Client.fromHttpApp(HttpRoutes.of[IO] {
      case GET -> Root / "v4" / "container-id" => Ok(container)
      case GET -> Root / "v4" / "container-id" / "task" => Ok(task)
    }.orNotFound)

  private def attribute(resource: OTResource, key: String): Option[String] = Option(resource.getAttribute(stringKey(key)))
  private def listAttribute(resource: OTResource, key: String): Option[List[String]] =
    Option(resource.getAttribute(stringArrayKey(key))).map(_.asScala.toList)

  test("an EC2 response without AvailabilityZone and with a short cluster name gives the attributes that are present") {
    EcsResource.fromMetadataUri(metadataServer(ec2Container, ec2Task), metadataUri).map { resource =>
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
    EcsResource.fromMetadataUri(metadataServer(fargateContainer, fargateTask), metadataUri).map { resource =>
      assertEquals(attribute(resource, "aws.ecs.cluster.arn"), "arn:aws:ecs:us-west-2:111122223333:cluster/default".some)
      assertEquals(attribute(resource, "cloud.availability_zone"), "us-west-2d".some)
      assertEquals(attribute(resource, "aws.ecs.launchtype"), "fargate".some)
      assertEquals(listAttribute(resource, "container.image.tags"), List("1.2.3").some)
    }
  }

  test("non-awslogs log drivers add no aws.log attributes") {
    EcsResource.fromMetadataUri(metadataServer(fargateContainer, fargateTask), metadataUri).map { resource =>
      assertEquals(listAttribute(resource, "aws.log.group.names"), None)
      assertEquals(listAttribute(resource, "aws.log.stream.names"), None)
    }
  }

  test("an old agent's response without LaunchType, logs, or ContainerARN still gives the task attributes") {
    EcsResource.fromMetadataUri(metadataServer(minimalContainer, minimalTask), metadataUri).map { resource =>
      assertEquals(attribute(resource, "aws.ecs.task.id"), "0123".some)
      assertEquals(attribute(resource, "aws.ecs.launchtype"), None)
      assertEquals(attribute(resource, "aws.ecs.container.arn"), None)
    }
  }

  test("an HTTP error gives the empty resource") {
    val failing = Client.fromHttpApp(HttpApp[IO](_ => InternalServerError()))
    EcsResource.fromMetadataUri(failing, metadataUri).map(assertEquals(_, OTResource.empty()))
  }

  test("an undecodable response gives the empty resource") {
    EcsResource.fromMetadataUri(metadataServer("""{"unexpected": true}""", ec2Task), metadataUri)
      .map(assertEquals(_, OTResource.empty()))
  }

  test("a hanging endpoint gives the empty resource after the timeout") {
    val hanging = Client.fromHttpApp(HttpApp[IO](_ => IO.never))
    TestControl.executeEmbed(EcsResource.fromMetadataUri(hanging, metadataUri).timed).map { case (elapsed, resource) =>
      assertEquals(resource, OTResource.empty())
      assertEquals(elapsed, EcsResource.timeout)
    }
  }

  test("without ECS_CONTAINER_METADATA_URI_V4, detection gives the empty resource without calling the client") {
    assume(sys.env.get("ECS_CONTAINER_METADATA_URI_V4").isEmpty, "this suite's JVM must not set ECS_CONTAINER_METADATA_URI_V4")
    val unreachable = Client.fromHttpApp(HttpApp[IO](req => IO.raiseError(new AssertionError(s"unexpected request $req"))))
    EcsResource.detect(unreachable).map(assertEquals(_, OTResource.empty()))
  }
}
