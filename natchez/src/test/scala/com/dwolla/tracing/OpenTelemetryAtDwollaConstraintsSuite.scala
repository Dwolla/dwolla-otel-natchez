package com.dwolla.tracing

import cats.effect.*
import cats.effect.std.{Dispatcher, Env, Random}
import munit.CatsEffectSuite
import natchez.EntryPoint
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory

class OpenTelemetryAtDwollaConstraintsSuite extends CatsEffectSuite {
  // two Env[IO] instances in scope make any implicit search for Env[IO] ambiguous, so the test below compiles only if
  // no public constructor asks for one
  implicit val env1: Env[IO] = Env.make[IO]
  implicit val env2: Env[IO] = Env.make[IO]

  test("the constructors don't demand Env") {
    implicit val loggerFactory: LoggerFactory[IO] = NoOpFactory[IO]

    // guard: the trap is armed, so the constructor calls below would fail to compile if any demanded Env
    assert(compileErrors("implicitly[Env[IO]]").toLowerCase.contains("ambiguous"), "two Env[IO] instances should make Env[IO] ambiguous")

    Random.scalaUtilRandomSeedLong[IO](20261012L).map { implicit random =>
      List[Resource[IO, EntryPoint[IO]]](
        OpenTelemetryAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local),
        OpenTelemetryAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local, logTraces = true),
        Dispatcher.parallel[IO](await = true).flatMap(OpenTelemetryAtDwolla[IO]("foo-service", Option("1.2.3"), DwollaEnvironment.Local, logTraces = true, _)),
      )
    }.map(constructed => assertEquals(constructed.size, 3))
  }
}
