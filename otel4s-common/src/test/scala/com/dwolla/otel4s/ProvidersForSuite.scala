package com.dwolla.otel4s

import cats.effect.IO
import munit.FunSuite
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider

class ProvidersForSuite extends FunSuite {
  private val tracerProvider: TracerProvider[IO] = TracerProvider.noop[IO]
  private val meterProvider: MeterProvider[IO] = MeterProvider.noop[IO]

  test("tracing alone selects the TracerProvider") {
    val selected: TracerProvider[IO] =
      implicitly[ProvidersFor.Aux[IO, Any with Signal.Tracing, TracerProvider[IO]]].apply(tracerProvider, meterProvider)
    assert(selected eq tracerProvider)
  }

  test("metrics alone selects the MeterProvider") {
    val selected: MeterProvider[IO] =
      implicitly[ProvidersFor.Aux[IO, Any with Signal.Metrics, MeterProvider[IO]]].apply(tracerProvider, meterProvider)
    assert(selected eq meterProvider)
  }

  test("both select (tracer, meter), whichever signal was added first") {
    val tracingFirst = implicitly[ProvidersFor.Aux[IO, Any with Signal.Tracing with Signal.Metrics, (TracerProvider[IO], MeterProvider[IO])]]
    val metricsFirst = implicitly[ProvidersFor.Aux[IO, Any with Signal.Metrics with Signal.Tracing, (TracerProvider[IO], MeterProvider[IO])]]
    val fromTracingFirst: (TracerProvider[IO], MeterProvider[IO]) = tracingFirst(tracerProvider, meterProvider)
    val fromMetricsFirst: (TracerProvider[IO], MeterProvider[IO]) = metricsFirst(tracerProvider, meterProvider)
    assertEquals(fromTracingFirst, (tracerProvider, meterProvider))
    assertEquals(fromMetricsFirst, (tracerProvider, meterProvider))
  }

  test("no signals has no instance, and says how to fix it") {
    val errors = compileErrors("implicitly[ProvidersFor[IO, Any]]")
    assert(errors.contains("enable at least one signal with withTracing and/or withMetrics"), errors)
  }
}
