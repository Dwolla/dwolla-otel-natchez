package com.dwolla.otel4s.oteljava

import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import com.dwolla.tracing.DwollaEnvironment
import io.opentelemetry.api.{GlobalOpenTelemetry, OpenTelemetry}
import munit.CatsEffectSuite
import org.typelevel.log4cats.testing.TestingLoggerFactory

import scala.jdk.CollectionConverters.*

class OtelJavaBuilderOptionsSuite extends CatsEffectSuite {
  private def withRandom[A](f: Random[IO] => IO[A]): IO[A] =
    Random.scalaUtilRandomSeedLong[IO](20261002L).flatMap(f)

  test("withLoggedSpans logs each finished span as JSON") {
    withRandom { implicit random =>
      implicit val loggerFactory: TestingLoggerFactory[IO] = TestingLoggerFactory.atomic[IO]()
      InMemoryTelemetry().attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withTracing.withLoggedSpans)
        .build
        .use(_.get("test").flatMap(_.span("logged-span").use_))
        .flatMap(_ => loggerFactory.logged)
        .map { logged =>
          assert(logged.exists(_.message.contains("\"name\":\"logged-span\"")), logged.map(_.message).mkString("\n"))
        }
    }
  }

  test("two SDKs in one JVM both start when neither registers globally") {
    withRandom { implicit random =>
      val builder = InMemoryTelemetry().attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withTracing)
      (builder.build, builder.build).tupled.use_
    }
  }

  test("registerGlobally makes the SDK GlobalOpenTelemetry") {
    withRandom { implicit random =>
      val telemetry = InMemoryTelemetry()
      telemetry.attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withTracing.registerGlobally)
        .build
        .use { _ =>
          IO(GlobalOpenTelemetry.get().getTracer("global").spanBuilder("global-span").startSpan().end()) >>
            IO(telemetry.spans.getFinishedSpanItems.asScala.exists(_.getName == "global-span"))
        }
        .guarantee(IO(GlobalOpenTelemetry.resetForTest()))
        .map(found => assert(found))
    }
  }

  test("startup hooks receive the running SDK and are released before it shuts down") {
    withRandom { implicit random =>
      val telemetry = InMemoryTelemetry()
      Ref.of[IO, Boolean](false).flatMap { sdkWasAliveAtRelease =>
        val hook: OpenTelemetry => Resource[IO, Unit] = otel =>
          Resource.make(IO.unit) { _ =>
            IO(otel.getTracer("hook").spanBuilder("released-by-hook").startSpan().end()) >>
              IO(telemetry.spans.getFinishedSpanItems.asScala.exists(_.getName == "released-by-hook"))
                .flatMap(sdkWasAliveAtRelease.set)
          }
        telemetry.attachTo(OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withTracing.withStartupHook(hook))
          .build
          .use_
          .flatMap(_ => sdkWasAliveAtRelease.get)
          .map(alive => assert(alive))
      }
    }
  }
}
