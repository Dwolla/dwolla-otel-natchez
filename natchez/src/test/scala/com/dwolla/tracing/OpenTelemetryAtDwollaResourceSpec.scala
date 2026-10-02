package com.dwolla.tracing

import io.opentelemetry.api.common.AttributeKey
import munit.FunSuite

class OpenTelemetryAtDwollaResourceSpec extends FunSuite {
  private def attribute(name: String)(attributes: io.opentelemetry.api.common.Attributes): Option[String] =
    Option(attributes.get(AttributeKey.stringKey(name)))

  test("Prod is labeled Production, matching Dwolla's infrastructure") {
    val attributes = OpenTelemetryAtDwolla.resourceAttributes("svc", DwollaEnvironment.Prod, None)

    assertEquals(attribute("deployment.environment.name")(attributes), Option("Production"))
  }

  test("other environments keep their name") {
    val attributes = OpenTelemetryAtDwolla.resourceAttributes("svc", DwollaEnvironment.DevInt, None)

    assertEquals(attribute("deployment.environment.name")(attributes), Option("DevInt"))
  }

  test("service.name is the given service name") {
    val attributes = OpenTelemetryAtDwolla.resourceAttributes("my-service", DwollaEnvironment.Uat, None)

    assertEquals(attribute("service.name")(attributes), Option("my-service"))
  }

  test("service.version is present when a version is given") {
    val attributes = OpenTelemetryAtDwolla.resourceAttributes("svc", DwollaEnvironment.Uat, Option("1.2.3"))

    assertEquals(attribute("service.version")(attributes), Option("1.2.3"))
  }

  test("service.version is absent when no version is given") {
    val attributes = OpenTelemetryAtDwolla.resourceAttributes("svc", DwollaEnvironment.Uat, None)

    assertEquals(attribute("service.version")(attributes), None)
  }
}
