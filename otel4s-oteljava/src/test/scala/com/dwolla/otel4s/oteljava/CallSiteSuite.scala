package com.dwolla.otel4s.oteljava

import cats.effect.*
import cats.effect.std.Random
import com.dwolla.tracing.DwollaEnvironment
import munit.CatsEffectSuite
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider

/** Stands in for otel4s-tagless / otel4s-smithy4s syntax: the same implicit signature. */
object LibrarySyntax {
  def tracerProviderInScope[F[_]](implicit tp: TracerProvider[F]): TracerProvider[F] = tp
  def meterProviderInScope[F[_]](implicit mp: MeterProvider[F]): MeterProvider[F] = mp
}

class CallSiteSuite extends CatsEffectSuite {
  private def builder(implicit random: Random[IO]) =
    InMemoryTelemetry().attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local))

  private def withRandom[A](f: Random[IO] => IO[A]): IO[A] =
    Random.scalaUtilRandomSeedLong[IO](20261002L).flatMap(f)

  test("a tracing-only result goes into implicit scope with `implicit tp =>`, no import") {
    withRandom { implicit random =>
      builder.withTracing.build.use { implicit tp =>
        IO(assert(LibrarySyntax.tracerProviderInScope[IO] eq tp))
      }
    }
  }

  test("a metrics-only result goes into implicit scope with `implicit mp =>`, no import") {
    withRandom { implicit random =>
      builder.withMetrics.build.use { implicit mp =>
        IO(assert(LibrarySyntax.meterProviderInScope[IO] eq mp))
      }
    }
  }

  test("both, Scala 2.13 without better-monadic-for: `implicit val (tp, mp) = pair`") {
    withRandom { implicit random =>
      builder.withTracing.withMetrics.build.use { pair =>
        implicit val (tp, mp) = pair
        IO(assert((LibrarySyntax.tracerProviderInScope[IO] eq tp) && (LibrarySyntax.meterProviderInScope[IO] eq mp)))
      }
    }
  }

  test("backend options need no import and chain before and after shared ones") {
    withRandom { implicit random =>
      val chained: Resource[IO, (TracerProvider[IO], MeterProvider[IO])] =
        builder.withStartupHook(_ => Resource.unit[IO]).withTracing.withStartupHook(_ => Resource.unit[IO]).withMetrics.build
      chained.use_
    }
  }

  test("enabling the signals in either order gives the same result type") {
    withRandom { implicit random =>
      val metricsFirst: Resource[IO, (TracerProvider[IO], MeterProvider[IO])] = builder.withMetrics.withTracing.build
      metricsFirst.use_
    }
  }

  test("a tracing-only result does not provide a MeterProvider") {
    val errors = compileErrors("(r: Random[IO]) => builder(r).withTracing.build.use { implicit tp => IO(LibrarySyntax.meterProviderInScope[IO]) }")
    assert((errors.contains("could not find implicit value") || errors.contains("No given instance")) && errors.contains("MeterProvider"), errors)
  }

  test("withLoggedSpans does not compile without withTracing") {
    val errors = compileErrors("(r: Random[IO], lf: org.typelevel.log4cats.LoggerFactory[IO]) => builder(r).withMetrics.withLoggedSpans(implicitly, lf)")
    assert(errors.contains("Signal.Tracing"), errors)
  }

  test("build does not compile with no signals") {
    val errors = compileErrors("(r: Random[IO]) => builder(r).build")
    assert(errors.contains("enable at least one signal with withTracing and/or withMetrics"), errors)
  }
}
