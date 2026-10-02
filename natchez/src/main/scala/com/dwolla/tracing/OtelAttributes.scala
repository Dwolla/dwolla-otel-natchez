package com.dwolla.tracing

import io.opentelemetry.api.common.AttributeKey.stringKey

/**
 * The [[https://github.com/open-telemetry/semantic-conventions-java?tab=readme-ov-file#published-releases
 * documentation for `io.opentelemetry.semconv:opentelemetry-semconv-incubating` states]]:
 *
 * <blockquote>'''NOTE:''' This artifact has the -alpha and comes with no compatibility
 * guarantees. Libraries can use this for testing, but should make copies of the attributes
 * to avoid possible runtime errors from version conflicts.</blockquote>
 *
 * The documentation for `io.opentelemetry.semconv:opentelemetry-semconv` states that its
 * semantic conventions are stable, but that the artifact itself comes with no compatibility
 * guarantees.
 *
 * Therefore, to avoid any issues, we copy the attributes we'll use, and only use the
 * artifacts in tests to confirm that the keys are properly defined.
 */
private[tracing] object OtelAttributes {
  private[tracing] val serviceName = stringKey("service.name")
  private[tracing] val serviceVersion = stringKey("service.version")
  private[tracing] val deploymentEnvironmentName = stringKey("deployment.environment.name")
}
