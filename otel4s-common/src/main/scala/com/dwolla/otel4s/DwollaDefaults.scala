package com.dwolla.otel4s

/**
 * Dwolla's default `otel.*` configuration properties. Backends apply them at the lowest priority, so any
 * `OTEL_*` environment variable or `otel.*` system property overrides them. They deliberately set no
 * metric views, histogram aggregation, or temporality, so instruments' bucket advice is honored.
 */
object DwollaDefaults {
  def properties[F[_]](settings: OtelAtDwollaSettings[F]): Map[String, String] =
    Map(
      "otel.traces.exporter" -> exporter(settings.tracingEnabled),
      "otel.metrics.exporter" -> exporter(settings.metricsEnabled),
      "otel.logs.exporter" -> "none",
      "otel.propagators" -> "tracecontext,b3multi,xray",
      // explicit, in case the Java SDK's default protocol changes
      "otel.exporter.otlp.protocol" -> "grpc",
      "otel.exporter.otlp.compression" -> "gzip",
      // smaller export batches keep payloads well under 4 MiB
      "otel.bsp.max.export.batch.size" -> "128",
    )

  /**
   * Dwolla's service name, supplied only when the application configured none. The SDK applies
   * `otel.service.name` after `otel.resource.attributes`, so supplying it unconditionally would overwrite a
   * `service.name` set through `OTEL_RESOURCE_ATTRIBUTES`.
   */
  def serviceName[F[_]](settings: OtelAtDwollaSettings[F],
                        configuredServiceName: Option[String],
                        configuredResourceAttributes: Map[String, String]): Map[String, String] =
    if (configuredServiceName.exists(_.nonEmpty) || configuredResourceAttributes.contains("service.name")) Map.empty
    else Map("otel.service.name" -> settings.serviceName)

  private def exporter(enabled: Boolean): String =
    if (enabled) "otlp" else "none"
}
