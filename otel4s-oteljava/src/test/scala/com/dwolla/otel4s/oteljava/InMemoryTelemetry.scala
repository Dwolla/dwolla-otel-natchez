package com.dwolla.otel4s.oteljava

import com.dwolla.otel4s.OtelAtDwollaBuilder
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.testing.exporter.{InMemoryMetricReader, InMemorySpanExporter}
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor

import scala.jdk.CollectionConverters.*

final class InMemoryTelemetry private (val spans: InMemorySpanExporter, val metrics: InMemoryMetricReader) {
  def attachTo[F[_], E](builder: OtelAtDwollaBuilder[F, OtelJavaBackend[F], E],
                        overrides: Map[String, String] = Map.empty): OtelAtDwollaBuilder[F, OtelJavaBackend[F], E] =
    OtelJavaBackend.OtelJavaBuilderOps(builder).withAutoConfigureCustomizer {
      _.addPropertiesCustomizer { (_: ConfigProperties) =>
          (Map("otel.traces.exporter" -> "none", "otel.metrics.exporter" -> "none") ++ overrides).asJava
        }
        .addTracerProviderCustomizer { (tracerProvider: SdkTracerProviderBuilder, _: ConfigProperties) =>
          tracerProvider.addSpanProcessor(SimpleSpanProcessor.create(spans))
        }
        .addMeterProviderCustomizer { (meterProvider: SdkMeterProviderBuilder, _: ConfigProperties) =>
          meterProvider.registerMetricReader(metrics)
        }
    }
}

object InMemoryTelemetry {
  def apply(): InMemoryTelemetry = new InMemoryTelemetry(InMemorySpanExporter.create(), InMemoryMetricReader.create())
}
