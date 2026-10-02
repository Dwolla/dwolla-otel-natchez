package com.dwolla.otel4s

import cats.effect.Resource
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider

/**
 * Starts an OpenTelemetry SDK for [[OtelAtDwollaBuilder]]. Implemented only by this library's backend
 * modules (the constructor is package-private). A member added here later must have a default
 * implementation, so a backend compiled against an older version of this module still links.
 */
abstract class Backend[F[_]] private[otel4s] () {
  def start(settings: OtelAtDwollaSettings[F]): Resource[F, (TracerProvider[F], MeterProvider[F])]
}
