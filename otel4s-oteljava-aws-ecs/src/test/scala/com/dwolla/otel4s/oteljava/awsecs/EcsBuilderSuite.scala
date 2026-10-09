package com.dwolla.otel4s.oteljava.awsecs

import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import com.dwolla.otel4s.oteljava.{InMemoryTelemetry, OtelJavaAtDwolla}
import com.dwolla.tracing.DwollaEnvironment
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.sdk.resources.Resource as OTResource
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.implicits.*

import scala.jdk.CollectionConverters.*

/** Runs in its own forked JVM with ECS_CONTAINER_METADATA_URI_V4 set (build.sbt testGrouping). */
class EcsBuilderSuite extends CatsEffectSuite {
  import EcsMetadataFixtures.*

  private val metadataServer: Client[IO] =
    Client.fromHttpApp(HttpRoutes.of[IO] {
      case GET -> Root / "v4" / "container-id" => Ok(ec2Container)
      case GET -> Root / "v4" / "container-id" / "task" => Ok(ec2Task)
    }.orNotFound)

  private def withRandom[A](f: Random[IO] => IO[A]): IO[A] = Random.scalaUtilRandomSeedLong[IO](20261009L).flatMap(f)

  private def resources(configure: InMemoryTelemetry => Resource[IO, (org.typelevel.otel4s.trace.TracerProvider[IO], org.typelevel.otel4s.metrics.MeterProvider[IO])],
                        telemetry: InMemoryTelemetry): IO[(OTResource, OTResource)] =
    configure(telemetry).use { case (tracerProvider, meterProvider) =>
      tracerProvider.get("test").flatMap(_.span("ecs-span").use_) >>
        meterProvider.get("test").flatMap(_.counter[Long]("test.calls").create.flatMap(_.inc())) >>
        IO {
          val span = telemetry.spans.getFinishedSpanItems.asScala.head
          val metric = telemetry.metrics.collectAllMetrics().asScala.find(_.getName == "test.calls").get
          (span.getResource, metric.getResource)
        }
    }

  private val taskId = "158d1c8083dd49d6b527399fd6414f5c"

  test("the ECS metadata URI is set in this JVM") {
    assertEquals(sys.env.get("ECS_CONTAINER_METADATA_URI_V4"), "http://169.254.170.2/v4/container-id".some)
  }

  test("withEcsResource puts the ECS attributes on spans only") {
    withRandom { implicit random =>
      resources(t => t.attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withTracing.withMetrics)
        .withEcsResource(metadataServer).build, InMemoryTelemetry())
        .map { case (spanResource, metricResource) =>
          assertEquals(spanResource.getAttribute(stringKey("aws.ecs.task.id")), taskId)
          assertEquals(metricResource.getAttribute(stringKey("aws.ecs.task.id")), null)
        }
    }
  }

  test("withEcsResourceOnMetrics puts the ECS attributes on metrics") {
    withRandom { implicit random =>
      resources(t => t.attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withTracing.withMetrics)
        .withEcsResourceOnMetrics(metadataServer).build, InMemoryTelemetry())
        .map { case (spanResource, metricResource) =>
          assertEquals(spanResource.getAttribute(stringKey("aws.ecs.task.id")), null)
          assertEquals(metricResource.getAttribute(stringKey("aws.ecs.task.id")), taskId)
        }
    }
  }

  test("both opt-ins put the ECS attributes on spans and metrics") {
    withRandom { implicit random =>
      resources(t => t.attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withTracing.withMetrics)
        .withEcsResource(metadataServer).withEcsResourceOnMetrics(metadataServer).build, InMemoryTelemetry())
        .map { case (spanResource, metricResource) =>
          assertEquals(spanResource.getAttribute(stringKey("aws.ecs.task.id")), taskId)
          assertEquals(metricResource.getAttribute(stringKey("aws.ecs.task.id")), taskId)
        }
    }
  }
}
