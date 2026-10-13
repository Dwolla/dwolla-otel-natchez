package com.dwolla.tracing

import munit.FunSuite

class OtelDefaultPropertiesSpec extends FunSuite {
  private def expected(tracesExporter: String, metricsExporter: String): Map[String, String] =
    Map(
      "otel.traces.exporter" -> tracesExporter,
      "otel.metrics.exporter" -> metricsExporter,
      "otel.logs.exporter" -> "none",
      "otel.propagators" -> "tracecontext,b3multi,xray",
      "otel.exporter.otlp.protocol" -> "grpc",
      "otel.exporter.otlp.compression" -> "gzip",
      "otel.bsp.max.export.batch.size" -> "128",
    )

  List(
    (true, true, "otlp", "otlp"),
    (true, false, "otlp", "none"),
    (false, true, "none", "otlp"),
    (false, false, "none", "none"),
  ).foreach { case (tracing, metrics, tracesExporter, metricsExporter) =>
    test(s"properties(tracingEnabled = $tracing, metricsEnabled = $metrics) use $tracesExporter traces and $metricsExporter metrics") {
      assertEquals(
        OtelDefaultProperties.properties(tracingEnabled = tracing, metricsEnabled = metrics),
        expected(tracesExporter, metricsExporter),
      )
    }
  }

  test("serviceName is supplied when neither a service name nor a service.name resource attribute is configured") {
    assertEquals(
      OtelDefaultProperties.serviceName("my-service", None, Map("other" -> "value")),
      Map("otel.service.name" -> "my-service"),
    )
  }

  test("serviceName is supplied when the configured service name is empty") {
    assertEquals(
      OtelDefaultProperties.serviceName("my-service", Some(""), Map.empty),
      Map("otel.service.name" -> "my-service"),
    )
  }

  test("serviceName is omitted when a service name is configured") {
    assertEquals(OtelDefaultProperties.serviceName("my-service", Some("configured"), Map.empty), Map.empty[String, String])
  }

  test("serviceName is omitted when service.name is a configured resource attribute") {
    assertEquals(
      OtelDefaultProperties.serviceName("my-service", None, Map("service.name" -> "configured")),
      Map.empty[String, String],
    )
  }
}
