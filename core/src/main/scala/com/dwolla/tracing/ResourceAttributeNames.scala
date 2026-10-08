package com.dwolla.tracing

/**
 * OpenTelemetry semantic-convention resource attribute names, as plain strings so this module needs no
 * OpenTelemetry dependency. Copied rather than referenced from the semconv artifacts for the reason given
 * on `OtelAttributes`.
 */
private[dwolla] object ResourceAttributeNames {
  val serviceName = "service.name"
  val serviceVersion = "service.version"
  val deploymentEnvironmentName = "deployment.environment.name"
}
