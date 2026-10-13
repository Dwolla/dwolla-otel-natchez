package com.dwolla.tracing

import cats.effect.*
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData

import scala.jdk.CollectionConverters.*

/** Records the spans of an `OpenTelemetryAtDwolla` SDK in memory, through test-only autoconfigure customizers. */
final class InMemoryTracing private (val spans: InMemorySpanExporter) {
  /** `overrides` outrank everything but later customizers, as an `OTEL_*` environment variable would outrank Dwolla's defaults. */
  def customizers(overrides: Map[String, String] = Map.empty): List[AutoConfiguredOpenTelemetrySdkBuilder => AutoConfiguredOpenTelemetrySdkBuilder] =
    List(
      _.addPropertiesCustomizer { (_: ConfigProperties) =>
          (Map("otel.traces.exporter" -> "none") ++ overrides).asJava
        }
        .addTracerProviderCustomizer { (tracerProvider: SdkTracerProviderBuilder, _: ConfigProperties) =>
          tracerProvider.addSpanProcessor(SimpleSpanProcessor.create(spans))
        }
    )

  // InMemorySpanExporter discards its spans when the SDK shuts down, so read them before the Resource closes
  def finishedSpans: IO[List[SpanData]] =
    IO(spans.getFinishedSpanItems.asScala.toList)
}

object InMemoryTracing {
  def apply(): InMemoryTracing = new InMemoryTracing(InMemorySpanExporter.create())

  /**
   * The configuration autoconfigure resolved for the SDK that `build` starts. `build` must apply the given
   * customizer after every other one, so it sees the final configuration.
   */
  def resolvedConfig(build: (AutoConfiguredOpenTelemetrySdkBuilder => AutoConfiguredOpenTelemetrySdkBuilder) => Resource[IO, natchez.EntryPoint[IO]]): IO[ConfigProperties] =
    (Dispatcher.sequential[IO], Deferred[IO, ConfigProperties].toResource).tupled.use { case (dispatcher, captured) =>
      val capture: AutoConfiguredOpenTelemetrySdkBuilder => AutoConfiguredOpenTelemetrySdkBuilder =
        _.addPropertiesCustomizer { (config: ConfigProperties) =>
          dispatcher.unsafeRunAndForget(captured.complete(config))
          Map.empty[String, String].asJava
        }
      build(capture).use_ >> captured.get
    }
}
