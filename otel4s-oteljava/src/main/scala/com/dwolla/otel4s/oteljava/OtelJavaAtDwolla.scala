package com.dwolla.otel4s.oteljava

import cats.effect.Async
import cats.effect.std.Random
import com.dwolla.otel4s.OtelAtDwollaBuilder
import com.dwolla.tracing.DwollaEnvironment
import org.typelevel.otel4s.oteljava.context.LocalContextProvider

/**
 * Configures otel4s on the OpenTelemetry Java SDK with Dwolla's defaults:
 *
 * {{{
 * OtelJavaAtDwolla[IO]("foo-service", BuildInfo.version, env).withTracing.withMetrics.build
 * }}}
 *
 * Any `OTEL_*` environment variable or `otel.*` system property overrides a default.
 */
object OtelJavaAtDwolla {
  def apply[F[_] : Async : LocalContextProvider : Random](serviceName: String,
                                                          serviceVersion: String,
                                                          env: DwollaEnvironment): OtelAtDwollaBuilder[F, OtelJavaBackend[F], Any] =
    OtelAtDwollaBuilder[F, OtelJavaBackend[F]](serviceName, serviceVersion, env, OtelJavaBackend[F])
}
