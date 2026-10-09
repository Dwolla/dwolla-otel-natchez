package com.dwolla.otel4s.oteljava

import io.opentelemetry.sdk.resources.Resource as OTResource

/**
 * A resource detected once, before the SDK starts, and added to the providers it names. Detected attributes never
 * override configured ones: keys in `otel.resource.attributes` or `otel.resource.disabled.keys` are dropped first.
 *
 * Although package-private, this class is used by another published module (`dwolla-otel4s-oteljava-aws-ecs`), so
 * changing it is binary-breaking. `detect` must not fail, because a failure fails the SDK's `start`.
 */
private[otel4s] final class SignalResource[F[_]](val detect: F[OTResource],
                                                 val onTraces: Boolean,
                                                 val onMetrics: Boolean)
