package com.dwolla.otel4s

import com.dwolla.tracing.DwollaEnvironment
import org.typelevel.log4cats.LoggerFactory

/** What a backend needs to start an SDK. Built only by [[OtelAtDwollaBuilder]]. */
sealed abstract class OtelAtDwollaSettings[F[_]] private[otel4s] () {
  def serviceName: String
  def serviceVersion: String
  def environment: DwollaEnvironment
  def tracingEnabled: Boolean
  def metricsEnabled: Boolean
  def spanLogging: Option[LoggerFactory[F]]

  private[otel4s] def enableTracing: OtelAtDwollaSettings[F]
  private[otel4s] def enableMetrics: OtelAtDwollaSettings[F]
  private[otel4s] def logSpansWith(loggerFactory: LoggerFactory[F]): OtelAtDwollaSettings[F]
}

object OtelAtDwollaSettings {
  private[otel4s] def apply[F[_]](serviceName: String,
                                  serviceVersion: String,
                                  environment: DwollaEnvironment): OtelAtDwollaSettings[F] =
    Impl(serviceName, serviceVersion, environment, tracingEnabled = false, metricsEnabled = false, spanLogging = None)

  private final case class Impl[F[_]](serviceName: String,
                                      serviceVersion: String,
                                      environment: DwollaEnvironment,
                                      tracingEnabled: Boolean,
                                      metricsEnabled: Boolean,
                                      spanLogging: Option[LoggerFactory[F]],
                                     ) extends OtelAtDwollaSettings[F] {
    override private[otel4s] def enableTracing: OtelAtDwollaSettings[F] = copy(tracingEnabled = true)
    override private[otel4s] def enableMetrics: OtelAtDwollaSettings[F] = copy(metricsEnabled = true)
    override private[otel4s] def logSpansWith(loggerFactory: LoggerFactory[F]): OtelAtDwollaSettings[F] =
      copy(spanLogging = Some(loggerFactory))
  }
}
