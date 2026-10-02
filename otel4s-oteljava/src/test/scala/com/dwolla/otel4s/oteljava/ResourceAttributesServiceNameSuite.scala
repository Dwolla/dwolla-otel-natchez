package com.dwolla.otel4s.oteljava

import cats.effect.*
import cats.effect.std.Random
import com.dwolla.tracing.DwollaEnvironment
import io.opentelemetry.api.common.AttributeKey.stringKey
import munit.CatsEffectSuite

import scala.jdk.CollectionConverters.*

/** Runs in its own forked JVM, with the environment variables build.sbt's testGrouping sets. */
class ResourceAttributesServiceNameSuite extends CatsEffectSuite {
  test("service.name from OTEL_RESOURCE_ATTRIBUTES wins; Dwolla's other attributes remain") {
    assertEquals(
      sys.env.get("OTEL_RESOURCE_ATTRIBUTES"),
      Some("service.name=service-from-resource-attributes"),
      "ResourceAttributesServiceNameSuite must run in sbt's resource-attributes-service-name test group (build.sbt testGrouping), which sets only OTEL_RESOURCE_ATTRIBUTES",
    )
    assertEquals(
      sys.env.get("OTEL_SERVICE_NAME"),
      None,
      "the resource-attributes-service-name test group must not set OTEL_SERVICE_NAME",
    )
    Random.scalaUtilRandomSeedLong[IO](20261002L).flatMap { implicit random =>
      val telemetry = InMemoryTelemetry()
      // InMemorySpanExporter discards its spans when the SDK shuts down, so read them before `build`'s Resource closes
      telemetry.attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.DevInt).withTracing)
        .build
        .use(_.get("test").flatMap(_.span("resource-span").use_) >> IO(telemetry.spans.getFinishedSpanItems.asScala.toList))
        .map { spans =>
          val resource = spans.head.getResource
          assertEquals(resource.getAttribute(stringKey("service.name")), "service-from-resource-attributes")
          assertEquals(resource.getAttribute(stringKey("service.version")), "1.2.3")
          assertEquals(resource.getAttribute(stringKey("deployment.environment.name")), DwollaEnvironment.DevInt.name)
        }
    }
  }
}
