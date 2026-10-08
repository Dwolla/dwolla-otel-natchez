package com.dwolla.otel4s

import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider

import scala.annotation.implicitNotFound

/**
 * Chooses what a builder's `build` returns from the signals it enabled: a `TracerProvider[F]`, a
 * `MeterProvider[F]`, or both as `(TracerProvider[F], MeterProvider[F])`, tracer first regardless of the
 * order the signals were enabled in.
 */
@implicitNotFound("enable at least one signal with withTracing and/or withMetrics before calling build")
sealed trait ProvidersFor[F[_], Enabled] {
  type Out
  def apply(tracerProvider: TracerProvider[F], meterProvider: MeterProvider[F]): Out
}

object ProvidersFor {
  type Aux[F[_], Enabled, O] = ProvidersFor[F, Enabled] { type Out = O }

  implicit def tracing[F[_]]: Aux[F, Any with Signal.Tracing, TracerProvider[F]] =
    new ProvidersFor[F, Any with Signal.Tracing] {
      override type Out = TracerProvider[F]
      override def apply(tracerProvider: TracerProvider[F], meterProvider: MeterProvider[F]): TracerProvider[F] =
        tracerProvider
    }

  implicit def metrics[F[_]]: Aux[F, Any with Signal.Metrics, MeterProvider[F]] =
    new ProvidersFor[F, Any with Signal.Metrics] {
      override type Out = MeterProvider[F]
      override def apply(tracerProvider: TracerProvider[F], meterProvider: MeterProvider[F]): MeterProvider[F] =
        meterProvider
    }

  implicit def tracingAndMetrics[F[_]]: Aux[F, Any with Signal.Tracing with Signal.Metrics, (TracerProvider[F], MeterProvider[F])] =
    new ProvidersFor[F, Any with Signal.Tracing with Signal.Metrics] {
      override type Out = (TracerProvider[F], MeterProvider[F])
      override def apply(tracerProvider: TracerProvider[F], meterProvider: MeterProvider[F]): (TracerProvider[F], MeterProvider[F]) =
        (tracerProvider, meterProvider)
    }
}
