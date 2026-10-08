package com.dwolla.otel4s.oteljava

import cats.effect._
import cats.effect.std.Random
import com.dwolla.tracing.DwollaEnvironment
import munit.CatsEffectSuite
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider

class Scala213CallSiteSuite extends CatsEffectSuite {
  test("both, Scala 2.13 with better-monadic-for: `implicit0` in a for-comprehension, no import") {
    val program = for {
      implicit0(random: Random[IO]) <- Random.scalaUtilRandomSeedLong[IO](20261002L).toResource
      (implicit0(tp: TracerProvider[IO]), implicit0(mp: MeterProvider[IO])) <-
        InMemoryTelemetry().attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withTracing.withMetrics).build
    } yield (LibrarySyntax.tracerProviderInScope[IO] eq tp) && (LibrarySyntax.meterProviderInScope[IO] eq mp)
    program.use(found => IO(assert(found)))
  }
}
