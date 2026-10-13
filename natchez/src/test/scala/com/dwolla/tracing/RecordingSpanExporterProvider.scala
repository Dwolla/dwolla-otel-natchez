package com.dwolla.tracing

import cats.effect.{Ref, SyncIO}
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.`export`.SpanExporter
import io.opentelemetry.sdk.trace.data.SpanData

/** An exporter loaded through autoconfigure's SPI and selected with `otel.traces.exporter`, recording each export's batch size. */
class RecordingSpanExporterProvider extends ConfigurableSpanExporterProvider {
  override def createExporter(config: ConfigProperties): SpanExporter =
    new SpanExporter {
      override def `export`(spans: java.util.Collection[SpanData]): CompletableResultCode = {
        RecordingSpanExporterProvider.exportedBatchSizes.update(_ :+ spans.size).unsafeRunSync()
        CompletableResultCode.ofSuccess()
      }
      override def flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
      override def shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
    }

  override def getName: String = RecordingSpanExporterProvider.name
}

object RecordingSpanExporterProvider {
  val name: String = "dwolla-test-recording"

  // ServiceLoader constructs the provider, so the test can only reach what it records through shared state
  val exportedBatchSizes: Ref[SyncIO, Vector[Int]] = Ref.unsafe[SyncIO, Vector[Int]](Vector.empty)
}
