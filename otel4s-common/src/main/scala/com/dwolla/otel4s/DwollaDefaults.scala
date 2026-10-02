package com.dwolla.otel4s

/**
 * Dwolla's default `otel.*` configuration properties. Backends apply them at the lowest priority, so any
 * `OTEL_*` environment variable or `otel.*` system property overrides them. They deliberately set no
 * metric views, histogram aggregation, or temporality, so instruments' bucket advice is honored.
 */
object DwollaDefaults {
  def properties[F[_]](settings: OtelAtDwollaSettings[F]): Map[String, String] =
    Map(
      "otel.service.name" -> settings.serviceName,
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

  private def exporter(enabled: Boolean): String =
    if (enabled) "otlp" else "none"
}
