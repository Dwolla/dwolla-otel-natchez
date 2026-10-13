package com.dwolla.tracing

import cats.effect.*
import cats.syntax.all.*
import munit.CatsEffectSuite

/** Runs in its own forked JVM, with the environment variables build.sbt's testGrouping sets. */
class OpenTelemetryAtDwollaBatchSpanProcessorSuite extends CatsEffectSuite {
  test("OTEL_BSP_* environment variables tune the batch span processor autoconfigure builds") {
    assertEquals(
      (sys.env.get("OTEL_TRACES_EXPORTER"), sys.env.get("OTEL_BSP_MAX_EXPORT_BATCH_SIZE")),
      (Some(RecordingSpanExporterProvider.name), Some("1")),
      "OpenTelemetryAtDwollaBatchSpanProcessorSuite must run in sbt's batch-span-processor test group (build.sbt testGrouping), which sets OTEL_TRACES_EXPORTER and OTEL_BSP_MAX_EXPORT_BATCH_SIZE",
    )
    // no test customizers, so the exporter and batch span processor come from autoconfigure alone
    OpenTelemetryAtDwolla.buildOtel[IO]("foo-service", DwollaEnvironment.DevInt, None, None, None, None, Nil)
      .use(ep => List("first-span", "second-span", "third-span").traverse_(ep.root(_).use_))
      .flatMap(_ => RecordingSpanExporterProvider.exportedBatchSizes.get.to[IO])
      .map(batchSizes => assertEquals(batchSizes, Vector(1, 1, 1)))
  }
}
