package com.dwolla.tracing

import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.incubating.DeploymentIncubatingAttributes
import munit.FunSuite

class OtelAttributeNameSpec extends FunSuite {

  test("Service Name") {
    assertEquals(OtelAttributes.serviceName, ServiceAttributes.SERVICE_NAME)
  }

  test("Service Version") {
    assertEquals(OtelAttributes.serviceVersion, ServiceAttributes.SERVICE_VERSION)
  }

  test("Deployment Environment") {
    assertEquals(OtelAttributes.deploymentEnvironmentName, DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME)
  }

}
