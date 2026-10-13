package com.dwolla.otel4s.oteljava

import io.opentelemetry.sdk.resources.Resource as OTResource

/**
 * The backend's internal record of a resource added with `withTelemetryResourceOnTraces` or
 * `withTelemetryResourceOnMetrics`: the detection to run once before the SDK starts, and the providers that receive
 * its result.
 */
private[oteljava] final class SignalResource[F[_]](val detect: F[OTResource],
                                                   val onTraces: Boolean,
                                                   val onMetrics: Boolean)
