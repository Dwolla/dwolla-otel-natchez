package com.dwolla.otel4s.oteljava.runtimemetrics

import cats.effect.*
import cats.effect.std.Random
import com.dwolla.otel4s.oteljava.{InMemoryTelemetry, OtelJavaAtDwolla}
import com.dwolla.tracing.DwollaEnvironment
import munit.CatsEffectSuite

import scala.jdk.CollectionConverters.*

class RuntimeMetricsSuite extends CatsEffectSuite {
  test("withRuntimeMetrics records jvm.* metrics through the configured SDK (links against otelV)") {
    Random.scalaUtilRandomSeedLong[IO](20261002L).flatMap { implicit random =>
      val telemetry = InMemoryTelemetry()
      telemetry.attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withMetrics.withRuntimeMetrics)
        .build
        .use(_ => IO(telemetry.metrics.collectAllMetrics().asScala.map(_.getName).toList))
        .map { names =>
          val jvmMetrics = names.filter(_.startsWith("jvm."))
          // memory, threads, and class loading are each recorded by a separate runtime-telemetry observer
          assert(jvmMetrics.exists(_.startsWith("jvm.memory.")), names.mkString(", "))
          assert(jvmMetrics.exists(_.startsWith("jvm.thread.")), names.mkString(", "))
          assert(jvmMetrics.exists(_.startsWith("jvm.class.")), names.mkString(", "))
        }
    }
  }

  test("withRuntimeMetrics does not compile without withMetrics") {
    val errors = compileErrors(
      "Random.scalaUtilRandomSeedLong[IO](1L).map { implicit random => OtelJavaAtDwolla[IO](\"foo-service\", \"1.2.3\", DwollaEnvironment.Local).withTracing.withRuntimeMetrics }"
    )
    assert(errors.contains("Signal.Metrics"), errors)
  }
}
