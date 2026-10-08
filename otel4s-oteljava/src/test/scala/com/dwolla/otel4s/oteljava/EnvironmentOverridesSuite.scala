package com.dwolla.otel4s.oteljava

import cats.effect.*
import cats.effect.std.Random
import com.dwolla.tracing.DwollaEnvironment
import io.opentelemetry.api.common.AttributeKey.stringKey
import munit.CatsEffectSuite

import scala.jdk.CollectionConverters.*

/** Runs in its own forked JVM, with the environment variables build.sbt's testGrouping sets. */
class EnvironmentOverridesSuite extends CatsEffectSuite {
  test("OTEL_* environment variables override Dwolla's defaults; unmentioned Dwolla attributes remain") {
    assertEquals(
      sys.env.get("OTEL_SERVICE_NAME"),
      Some("service-from-env"),
      "EnvironmentOverridesSuite must run in sbt's environment-overrides test group (build.sbt testGrouping), which sets OTEL_SERVICE_NAME and OTEL_RESOURCE_ATTRIBUTES",
    )
    Random.scalaUtilRandomSeedLong[IO](20261002L).flatMap { implicit random =>
      val telemetry = InMemoryTelemetry()
      // InMemorySpanExporter discards its spans when the SDK shuts down, so read them before `build`'s Resource closes
      telemetry.attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.DevInt).withTracing)
        .build
        .use(_.get("test").flatMap(_.span("env-span").use_) >> IO(telemetry.spans.getFinishedSpanItems.asScala.toList))
        .map { spans =>
          val resource = spans.head.getResource
          assertEquals(resource.getAttribute(stringKey("service.name")), "service-from-env")
          assertEquals(resource.getAttribute(stringKey("deployment.environment.name")), "env-from-env")
          assertEquals(resource.getAttribute(stringKey("service.version")), "1.2.3")
        }
    }
  }
}
