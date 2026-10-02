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

  property("Dwolla's transport and propagation defaults are always set") {
    Prop.forAll(settings) { s =>
      val properties = DwollaDefaults.properties(s)
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

  property("properties never include the service name, so OTEL_RESOURCE_ATTRIBUTES can supply it") {
    Prop.forAll(settings) { s =>
      assert(!DwollaDefaults.properties(s).contains("otel.service.name"))
    }
  }

  private val otherAttributes: Gen[Map[String, String]] =
    Gen.mapOf(Gen.zip(Gen.identifier.map("attr." + _), Gen.identifier))

  property("the service name is supplied when neither otel.service.name nor a service.name resource attribute is configured") {
    Prop.forAll(settings, Gen.oneOf(None, Some("")), otherAttributes) { (s, configuredName, attributes) =>
      assertEquals(DwollaDefaults.serviceName(s, configuredName, attributes), Map("otel.service.name" -> s.serviceName))
    }
  }

  property("a configured otel.service.name is left alone") {
    Prop.forAll(settings, Gen.identifier, otherAttributes) { (s, configuredName, attributes) =>
      assertEquals(DwollaDefaults.serviceName(s, Some(configuredName), attributes), Map.empty[String, String])
    }
  }

  property("a service.name resource attribute is left alone") {
    Prop.forAll(settings, Gen.oneOf(None, Some("")), otherAttributes, Gen.identifier) { (s, configuredName, attributes, fromAttributes) =>
      assertEquals(
        DwollaDefaults.serviceName(s, configuredName, attributes + ("service.name" -> fromAttributes)),
        Map.empty[String, String],
      )
    }
  }
}
