package com.dwolla.tracing

import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.incubating.DeploymentIncubatingAttributes
import munit.FunSuite

class ResourceAttributeNamesSpec extends FunSuite {

  test("service.name matches the semantic conventions") {
    assertEquals(ResourceAttributeNames.serviceName, ServiceAttributes.SERVICE_NAME.getKey)
  }

  test("service.version matches the semantic conventions") {
    assertEquals(ResourceAttributeNames.serviceVersion, ServiceAttributes.SERVICE_VERSION.getKey)
  }

  test("deployment.environment.name matches the semantic conventions") {
    assertEquals(ResourceAttributeNames.deploymentEnvironmentName, DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME.getKey)
  }

}
