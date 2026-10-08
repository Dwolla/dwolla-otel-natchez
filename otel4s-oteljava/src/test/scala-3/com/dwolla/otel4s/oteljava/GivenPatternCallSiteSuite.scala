package com.dwolla.otel4s.oteljava

import cats.effect.*
import cats.effect.std.Random
import com.dwolla.tracing.DwollaEnvironment
import munit.CatsEffectSuite
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider

class GivenPatternCallSiteSuite extends CatsEffectSuite {
  test("both, Scala 3: `case (given TracerProvider[IO], given MeterProvider[IO]) =>`, no import") {
    Random.scalaUtilRandomSeedLong[IO](20261002L).flatMap { implicit random =>
      InMemoryTelemetry().attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withTracing.withMetrics)
        .build
        .use { pair =>
          val found = pair match {
            case (given TracerProvider[IO], given MeterProvider[IO]) =>
              (LibrarySyntax.tracerProviderInScope[IO], LibrarySyntax.meterProviderInScope[IO])
          }
          IO(assert((found._1 eq pair._1) && (found._2 eq pair._2)))
        }
    }
  }
}
