package com.dwolla.otel4s.oteljava

import cats.effect.*
import cats.effect.std.Random
import com.dwolla.tracing.DwollaEnvironment
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.sdk.metrics.data.MetricDataType
import io.opentelemetry.sdk.trace.data.SpanData
import munit.CatsEffectSuite
import org.typelevel.otel4s.metrics.BucketBoundaries
import org.typelevel.otel4s.trace.TracerProvider

import scala.jdk.CollectionConverters.*

class OtelJavaAtDwollaSuite extends CatsEffectSuite {
  private def withRandom[A](f: Random[IO] => IO[A]): IO[A] =
    Random.scalaUtilRandomSeedLong[IO](20261002L).flatMap(f)

  // InMemorySpanExporter discards its spans when the SDK shuts down, so read them before `build`'s Resource closes
  private def finishedSpans(telemetry: InMemoryTelemetry, tracerProvider: Resource[IO, TracerProvider[IO]], spanName: String): IO[List[SpanData]] =
    tracerProvider.use(_.get("test").flatMap(_.span(spanName).use_) >> IO(telemetry.spans.getFinishedSpanItems.asScala.toList))

  test("spans carry X-Ray-compatible trace IDs whose first 8 hex digits are the epoch seconds") {
    withRandom { implicit random =>
      val telemetry = InMemoryTelemetry()
      for {
        before <- IO.realTime
        spans <- finishedSpans(telemetry, telemetry.attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withTracing).build, "x-ray-span")
        after <- IO.realTime
      } yield {
        val traceId = spans.map(_.getTraceId) match {
          case List(id) => id
          case other => fail(s"expected one span, got $other")
        }
        val epochSeconds = java.lang.Long.parseLong(traceId.take(8), 16)
        assert(epochSeconds >= before.toSeconds && epochSeconds <= after.toSeconds, s"$traceId vs [$before, $after]")
      }
    }
  }

  test("the resource carries service.name, service.version, and deployment.environment.name") {
    withRandom { implicit random =>
      val telemetry = InMemoryTelemetry()
      finishedSpans(telemetry, telemetry.attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.DevInt).withTracing).build, "resource-span")
        .map { spans =>
          val resource = spans.head.getResource
          assertEquals(resource.getAttribute(stringKey("service.name")), "foo-service")
          assertEquals(resource.getAttribute(stringKey("service.version")), "1.2.3")
          assertEquals(resource.getAttribute(stringKey("deployment.environment.name")), "DevInt")
        }
    }
  }

  test("production's deployment.environment.name is Production, as in Dwolla's infrastructure") {
    withRandom { implicit random =>
      val telemetry = InMemoryTelemetry()
      finishedSpans(telemetry, telemetry.attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Prod).withTracing).build, "prod-span")
        .map { spans =>
          assertEquals(spans.head.getResource.getAttribute(stringKey("deployment.environment.name")), "Production")
        }
    }
  }

  test("explicitly configured resource attributes win over Dwolla's, which fill in the rest") {
    withRandom { implicit random =>
      val telemetry = InMemoryTelemetry()
      finishedSpans(
        telemetry,
        telemetry.attachTo(
          OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.DevInt).withTracing,
          overrides = Map("otel.resource.attributes" -> "deployment.environment.name=configured-env"),
        ).build,
        "override-span",
      )
        .map { spans =>
          val resource = spans.head.getResource
          assertEquals(resource.getAttribute(stringKey("deployment.environment.name")), "configured-env")
          assertEquals(resource.getAttribute(stringKey("service.version")), "1.2.3")
        }
    }
  }

  test("a configured property overrides Dwolla's default") {
    withRandom { implicit random =>
      val telemetry = InMemoryTelemetry()
      finishedSpans(
        telemetry,
        telemetry.attachTo(
          OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withTracing,
          overrides = Map("otel.service.name" -> "configured-service"),
        ).build,
        "service-name-span",
      )
        .map { spans =>
          assertEquals(spans.head.getResource.getAttribute(stringKey("service.name")), "configured-service")
        }
    }
  }

  test("histograms keep their explicit bucket advice") {
    withRandom { implicit random =>
      val telemetry = InMemoryTelemetry()
      telemetry.attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withMetrics)
        .build
        .use { meterProvider =>
          meterProvider.get("test").flatMap { meter =>
            meter.histogram[Double]("test.duration")
              .withExplicitBucketBoundaries(BucketBoundaries(Vector(0.5, 1.0)))
              .create
              .flatMap(_.record(0.7))
          } >> IO(telemetry.metrics.collectAllMetrics().asScala.toList)
        }
        .map { metrics =>
          val histogram = metrics.find(_.getName == "test.duration").getOrElse(fail(s"no test.duration in $metrics"))
          assertEquals(histogram.getType, MetricDataType.HISTOGRAM)
          val boundaries = histogram.getHistogramData.getPoints.asScala.head.getBoundaries.asScala.map(_.doubleValue).toList
          assertEquals(boundaries, List(0.5, 1.0))
        }
    }
  }
}
