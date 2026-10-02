package com.dwolla.otel4s

import cats.effect.*
import cats.syntax.all.*
import com.dwolla.tracing.DwollaEnvironment
import munit.CatsEffectSuite
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.testing.TestingLoggerFactory
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider

class OtelAtDwollaBuilderSuite extends CatsEffectSuite {
  private val tracerProvider: TracerProvider[IO] = TracerProvider.noop[IO]
  private val meterProvider: MeterProvider[IO] = MeterProvider.noop[IO]

  private final class RecordingBackend(received: Ref[IO, Option[OtelAtDwollaSettings[IO]]]) extends Backend[IO] {
    override def start(settings: OtelAtDwollaSettings[IO]): Resource[IO, (TracerProvider[IO], MeterProvider[IO])] =
      received.set(settings.some).as((tracerProvider, meterProvider)).toResource
  }

  private def builder: IO[(OtelAtDwollaBuilder[IO, RecordingBackend, Any], IO[Option[OtelAtDwollaSettings[IO]]])] =
    Ref[IO].of(none[OtelAtDwollaSettings[IO]]).map { received =>
      (OtelAtDwollaBuilder[IO, RecordingBackend]("foo-service", "1.2.3", DwollaEnvironment.DevInt, new RecordingBackend(received)), received.get)
    }

  test("a tracing build starts the backend with tracing only, and returns its TracerProvider") {
    builder.flatMap { case (b, received) =>
      b.withTracing.build.use(tp => IO(assert(tp eq tracerProvider))) >>
        received.map { settings =>
          assertEquals(settings.map(s => (s.tracingEnabled, s.metricsEnabled)), (true, false).some)
          assertEquals(settings.map(s => (s.serviceName, s.serviceVersion, s.environment)), ("foo-service", "1.2.3", DwollaEnvironment.DevInt).some)
        }
    }
  }

  test("a metrics build returns the backend's MeterProvider") {
    builder.flatMap { case (b, received) =>
      b.withMetrics.build.use(mp => IO(assert(mp eq meterProvider))) >>
        received.map(s => assertEquals(s.map(x => (x.tracingEnabled, x.metricsEnabled)), (false, true).some))
    }
  }

  test("enabling both, in either order, returns (tracer, meter)") {
    builder.flatMap { case (b, _) =>
      (b.withMetrics.withTracing.build.use(_.pure[IO]), b.withTracing.withMetrics.build.use(_.pure[IO])).mapN { (metricsFirst, tracingFirst) =>
        assertEquals(metricsFirst, (tracerProvider, meterProvider))
        assertEquals(tracingFirst, (tracerProvider, meterProvider))
      }
    }
  }

  test("withLoggedSpans hands the backend the LoggerFactory") {
    implicit val loggerFactory: LoggerFactory[IO] = TestingLoggerFactory.atomic[IO]()
    builder.flatMap { case (b, received) =>
      b.withTracing.withLoggedSpans.build.use_ >>
        received.map(s => assert(s.flatMap(_.spanLogging).exists(_ eq loggerFactory)))
    }
  }

  test("withLoggedSpans does not compile without tracing") {
    val errors = compileErrors("builder.map(_._1.withMetrics.withLoggedSpans(implicitly, TestingLoggerFactory.atomic[IO]()))")
    assert(errors.contains("Signal.Tracing"), errors)
  }

  test("a tracing-only result is not a MeterProvider") {
    val errors = compileErrors("builder.flatMap(_._1.withTracing.build.use { implicit tp => IO(MeterProvider[IO]) })")
    assert((errors.contains("could not find implicit value") || errors.contains("No given instance")) && errors.contains("MeterProvider"), errors)
  }

  test("build with no signals does not compile") {
    val errors = compileErrors("builder.flatMap(_._1.build.use_)")
    assert(errors.contains("enable at least one signal with withTracing and/or withMetrics"), errors)
  }
}
