package com.dwolla.otel4s

import com.dwolla.tracing.OtelDefaultProperties

/**
 * Dwolla's default `otel.*` configuration properties. Backends apply them at the lowest priority, so any
 * `OTEL_*` environment variable or `otel.*` system property overrides them. The values, and the reasoning
 * behind them, live in [[com.dwolla.tracing.OtelDefaultProperties]], which this object delegates to.
 */
object DwollaDefaults {
  def properties[F[_]](settings: OtelAtDwollaSettings[F]): Map[String, String] =
    OtelDefaultProperties.properties(settings.tracingEnabled, settings.metricsEnabled)

  /**
   * Dwolla's service name, supplied only when the application configured none; see
   * [[com.dwolla.tracing.OtelDefaultProperties.serviceName]].
   */
  def serviceName[F[_]](settings: OtelAtDwollaSettings[F],
                        configuredServiceName: Option[String],
                        configuredResourceAttributes: Map[String, String]): Map[String, String] =
    OtelDefaultProperties.serviceName(settings.serviceName, configuredServiceName, configuredResourceAttributes)
}
