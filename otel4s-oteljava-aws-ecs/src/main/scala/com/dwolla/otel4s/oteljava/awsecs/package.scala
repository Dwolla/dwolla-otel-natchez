package com.dwolla.otel4s.oteljava

import cats.effect.Temporal
import cats.effect.std.Env
import com.dwolla.otel4s.{OtelAtDwollaBuilder, Signal}
import org.http4s.client.Client
import org.typelevel.log4cats.LoggerFactory

import scala.annotation.unused

/**
 * `import com.dwolla.otel4s.oteljava.awsecs._` to add ECS resource attributes (`aws.ecs.task.arn`,
 * `aws.ecs.task.id`, `cloud.*`, `container.*`, ...) detected from the ECS task metadata endpoint v4 at startup.
 *
 * `withEcsResource` adds them to spans and `withEcsResourceOnMetrics` adds them to metrics; to get both, call both.
 *
 * Detection never fails startup. Outside ECS (`ECS_CONTAINER_METADATA_URI_V4` unset or malformed) it adds nothing
 * and logs nothing. Inside ECS, an HTTP error, an undecodable response, or a 2-second timeout adds nothing and logs
 * one WARN, with the throwable, through the application's `LoggerFactory`. Each opt-in waits up to 2 seconds for
 * the metadata endpoint, so with both, startup can wait up to 4 seconds.
 *
 * Detected attributes override Dwolla's and other detected resource attributes, but not keys configured in
 * `OTEL_RESOURCE_ATTRIBUTES`; `OTEL_RESOURCE_DISABLED_KEYS` removes them.
 */
package object awsecs {
  implicit class EcsResourceOps[F[_], E](private val builder: OtelAtDwollaBuilder[F, OtelJavaBackend[F], E]) extends AnyVal {
    /**
     * Add ECS resource attributes to spans, detected at startup through `client`. Metrics are not affected; use
     * `withEcsResourceOnMetrics` as well to add them to metrics. Detection failures inside ECS are logged at WARN
     * through `loggerFactory`; outside ECS nothing is logged. Waits up to 2 seconds for the metadata endpoint. The
     * attributes override Dwolla's but not `OTEL_RESOURCE_ATTRIBUTES`, and `OTEL_RESOURCE_DISABLED_KEYS` removes
     * them.
     */
    def withEcsResource(client: Client[F])(implicit F: Temporal[F], env: Env[F], loggerFactory: LoggerFactory[F], @unused ev: E <:< Signal.Tracing): OtelAtDwollaBuilder[F, OtelJavaBackend[F], E] =
      OtelJavaBackend.OtelJavaBuilderOps(builder)
        .withResourceOnTraces(EcsResource.detect(client))

    /**
     * Add ECS resource attributes to metrics, detected at startup through `client`. Spans are not affected; use
     * `withEcsResource` as well to add them to spans. Every resource attribute is sent with every metric data
     * point, so with CloudWatch metrics, which bill OTLP ingestion by the byte, this adds cost for little value:
     * `service.instance.id` already keeps each instance's series apart. Detection runs again if `withEcsResource`
     * is also used, so with both, startup can wait up to 4 seconds (2 per opt-in). Detection failures inside ECS
     * are logged at WARN through `loggerFactory`; outside ECS nothing is logged. The attributes override Dwolla's
     * but not `OTEL_RESOURCE_ATTRIBUTES`, and `OTEL_RESOURCE_DISABLED_KEYS` removes them.
     */
    def withEcsResourceOnMetrics(client: Client[F])(implicit F: Temporal[F], env: Env[F], loggerFactory: LoggerFactory[F], @unused ev: E <:< Signal.Metrics): OtelAtDwollaBuilder[F, OtelJavaBackend[F], E] =
      OtelJavaBackend.OtelJavaBuilderOps(builder)
        .withResourceOnMetrics(EcsResource.detect(client))
  }
}
