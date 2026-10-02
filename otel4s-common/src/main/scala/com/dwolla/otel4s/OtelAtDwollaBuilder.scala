package com.dwolla.otel4s

import cats.effect.Resource
import com.dwolla.tracing.DwollaEnvironment
import org.typelevel.log4cats.LoggerFactory

import scala.annotation.unused

/**
 * Configures OpenTelemetry for otel4s with Dwolla's defaults. Obtain one from a backend's entry point
 * (e.g. `OtelJavaAtDwolla`), enable signals, then `build`.
 */
final class OtelAtDwollaBuilder[F[_], B <: Backend[F], Enabled] private (settings: OtelAtDwollaSettings[F], backend: B) {
  def withTracing: OtelAtDwollaBuilder[F, B, Enabled with Signal.Tracing] =
    new OtelAtDwollaBuilder(settings.enableTracing, backend)

  def withMetrics: OtelAtDwollaBuilder[F, B, Enabled with Signal.Metrics] =
    new OtelAtDwollaBuilder(settings.enableMetrics, backend)

  /** Also log every finished span, as OTLP-shaped JSON, through `LoggerFactory[F]`. */
  def withLoggedSpans(implicit @unused ev: Enabled <:< Signal.Tracing, L: LoggerFactory[F]): OtelAtDwollaBuilder[F, B, Enabled] =
    new OtelAtDwollaBuilder(settings.logSpansWith(L), backend)

  /**
   * Returns exactly what was enabled: `TracerProvider[F]`, `MeterProvider[F]`, or
   * `(TracerProvider[F], MeterProvider[F])`, tracer first. Call it once per application: every tracer and
   * meter the application uses should come from these providers.
   */
  def build(implicit providers: ProvidersFor[F, Enabled]): Resource[F, providers.Out] =
    backend.start(settings).map { case (tracerProvider, meterProvider) => providers(tracerProvider, meterProvider) }

  private[otel4s] def mapBackend(f: B => B): OtelAtDwollaBuilder[F, B, Enabled] =
    new OtelAtDwollaBuilder(settings, f(backend))
}

object OtelAtDwollaBuilder {
  private[otel4s] def apply[F[_], B <: Backend[F]](serviceName: String,
                                                   serviceVersion: String,
                                                   environment: DwollaEnvironment,
                                                   backend: B): OtelAtDwollaBuilder[F, B, Any] =
    new OtelAtDwollaBuilder(OtelAtDwollaSettings[F](serviceName, serviceVersion, environment), backend)
}
