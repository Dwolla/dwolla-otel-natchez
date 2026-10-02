package com.dwolla.otel4s

/** Type-level markers for the telemetry signals a builder has enabled. */
object Signal {
  sealed trait Tracing
  sealed trait Metrics
}
