package com.dwolla.otel4s

import cats.effect.IO
import com.dwolla.tracing.DwollaEnvironment
import munit.ScalaCheckSuite
import org.scalacheck.{Arbitrary, Gen, Prop}

class DwollaDefaultsSuite extends ScalaCheckSuite {
  private val environments: Gen[DwollaEnvironment] = Gen.oneOf(
    DwollaEnvironment.Local, DwollaEnvironment.DevInt, DwollaEnvironment.Uat,
    DwollaEnvironment.Prod, DwollaEnvironment.Sandbox, DwollaEnvironment.Admin,
  )

  private val settings: Gen[OtelAtDwollaSettings[IO]] =
    for {
      serviceName <- Gen.identifier
      serviceVersion <- Gen.identifier
      environment <- environments
      tracing <- Arbitrary.arbitrary[Boolean]
      metrics <- Arbitrary.arbitrary[Boolean]
    } yield {
      val base = OtelAtDwollaSettings[IO](serviceName, serviceVersion, environment)
      val traced = if (tracing) base.enableTracing else base
      if (metrics) traced.enableMetrics else traced
    }

  property("exporters are otlp exactly for the enabled signals, and logs are never exported") {
    Prop.forAll(settings) { s =>
      val properties = DwollaDefaults.properties(s)
      assertEquals(properties.get("otel.traces.exporter"), Some(if (s.tracingEnabled) "otlp" else "none"))
      assertEquals(properties.get("otel.metrics.exporter"), Some(if (s.metricsEnabled) "otlp" else "none"))
      assertEquals(properties.get("otel.logs.exporter"), Some("none"))
    }
  }

  property("the service name and Dwolla's transport and propagation defaults are always set") {
    Prop.forAll(settings) { s =>
      val properties = DwollaDefaults.properties(s)
      assertEquals(properties.get("otel.service.name"), Some(s.serviceName))
      assertEquals(properties.get("otel.propagators"), Some("tracecontext,b3multi,xray"))
      assertEquals(properties.get("otel.exporter.otlp.protocol"), Some("grpc"))
      assertEquals(properties.get("otel.exporter.otlp.compression"), Some("gzip"))
      assertEquals(properties.get("otel.bsp.max.export.batch.size"), Some("128"))
    }
  }

  property("no default overrides instrument bucket advice") {
    Prop.forAll(settings) { s =>
      val keys = DwollaDefaults.properties(s).keySet
      assert(!keys.exists(_.contains("histogram.aggregation")), keys)
      assert(!keys.exists(_.contains("temporality")), keys)
    }
  }
}
