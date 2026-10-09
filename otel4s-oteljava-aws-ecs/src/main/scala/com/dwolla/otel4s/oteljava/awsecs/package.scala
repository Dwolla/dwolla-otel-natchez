package com.dwolla.otel4s.oteljava

import cats.effect.Temporal
import cats.effect.std.Env
import com.dwolla.otel4s.{OtelAtDwollaBuilder, Signal}
import org.http4s.client.Client

import scala.annotation.unused

/**
 * `import com.dwolla.otel4s.oteljava.awsecs._` to add ECS resource attributes (`aws.ecs.task.arn`,
 * `aws.ecs.task.id`, `cloud.*`, `container.*`, ...) detected from the ECS task metadata endpoint v4 at startup.
 *
 * Detection never fails startup: outside ECS (`ECS_CONTAINER_METADATA_URI_V4` unset), or on any HTTP error,
 * undecodable response, or a 2-second timeout, it adds nothing, silently (the backend has no logger unless span
 * logging is enabled). Configured attributes (`OTEL_RESOURCE_ATTRIBUTES`) win, and `OTEL_RESOURCE_DISABLED_KEYS`
 * removes detected ones.
 */
package object awsecs {
  implicit class EcsResourceOps[F[_], E](private val builder: OtelAtDwollaBuilder[F, OtelJavaBackend[F], E]) extends AnyVal {
    /** Add ECS resource attributes to spans, detected once at startup through `client`. */
    def withEcsResource(client: Client[F])(implicit F: Temporal[F], env: Env[F], @unused ev: E <:< Signal.Tracing): OtelAtDwollaBuilder[F, OtelJavaBackend[F], E] =
      OtelJavaBackend.OtelJavaBuilderOps(builder)
        .withSignalResource(new SignalResource[F](EcsResource.detect(client), onTraces = true, onMetrics = false))

    /**
     * Also add ECS resource attributes to metrics. Every resource attribute is sent with every metric data point,
     * so with CloudWatch metrics, which bill OTLP ingestion by the byte, this adds cost for little value:
     * `service.instance.id` already keeps each instance's series apart. Detection runs again if
     * `withEcsResource` is also used. As with `withEcsResource`, detection failures are silent.
     */
    def withEcsResourceOnMetrics(client: Client[F])(implicit F: Temporal[F], env: Env[F], @unused ev: E <:< Signal.Metrics): OtelAtDwollaBuilder[F, OtelJavaBackend[F], E] =
      OtelJavaBackend.OtelJavaBuilderOps(builder)
        .withSignalResource(new SignalResource[F](EcsResource.detect(client), onTraces = false, onMetrics = true))
  }
}
