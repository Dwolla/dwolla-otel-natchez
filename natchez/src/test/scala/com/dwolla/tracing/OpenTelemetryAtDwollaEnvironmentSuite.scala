package com.dwolla.tracing

import cats.effect.*
import munit.CatsEffectSuite

/** Runs in its own forked JVM, with the environment variables build.sbt's testGrouping sets. */
class OpenTelemetryAtDwollaEnvironmentSuite extends CatsEffectSuite {
  private def entryPoint(tracing: InMemoryTracing) =
    OpenTelemetryAtDwolla.buildOtel[IO]("foo-service", DwollaEnvironment.DevInt, None, None, None, None, tracing.customizers())

  test("OTEL_TRACES_SAMPLER and OTEL_BSP_* environment variables override Dwolla's defaults") {
    assertEquals(
      (sys.env.get("OTEL_TRACES_SAMPLER"), sys.env.get("OTEL_BSP_MAX_QUEUE_SIZE")),
      (Some("always_off"), Some("4096")),
      "OpenTelemetryAtDwollaEnvironmentSuite must run in sbt's environment test group (build.sbt testGrouping), which sets OTEL_TRACES_SAMPLER and OTEL_BSP_MAX_QUEUE_SIZE",
    )
    val tracing = InMemoryTracing()
    for {
      spans <- entryPoint(tracing).use(_.root("unsampled-span").use_ >> tracing.finishedSpans)
      config <- InMemoryTracing.resolvedConfig(capture => OpenTelemetryAtDwolla.buildOtel[IO]("foo-service", DwollaEnvironment.DevInt, None, None, None, None, tracing.customizers() :+ capture))
    } yield {
      assertEquals(spans, Nil)
      assertEquals(config.getString("otel.bsp.max.queue.size"), "4096")
    }
  }
}
