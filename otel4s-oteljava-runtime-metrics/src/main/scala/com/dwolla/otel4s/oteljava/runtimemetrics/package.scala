package com.dwolla.otel4s.oteljava

import cats.effect.{Resource, Sync}
import cats.syntax.functor.*
import com.dwolla.otel4s.{OtelAtDwollaBuilder, Signal}
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry

import scala.annotation.unused

/** `import com.dwolla.otel4s.oteljava.runtimemetrics._` to add `.withRuntimeMetrics` to a metrics-enabled builder. */
package object runtimemetrics {
  implicit class RuntimeMetricsOps[F[_], E](private val builder: OtelAtDwollaBuilder[F, OtelJavaBackend[F], E]) extends AnyVal {
    /** Record JVM runtime metrics (memory, GC, threads, CPU, ...) through this SDK. */
    def withRuntimeMetrics(implicit F: Sync[F], @unused ev: E <:< Signal.Metrics): OtelAtDwollaBuilder[F, OtelJavaBackend[F], E] =
      builder.withStartupHook { openTelemetry =>
        Resource.fromAutoCloseable(Sync[F].delay(RuntimeTelemetry.create(openTelemetry))).void
      }
  }
}
