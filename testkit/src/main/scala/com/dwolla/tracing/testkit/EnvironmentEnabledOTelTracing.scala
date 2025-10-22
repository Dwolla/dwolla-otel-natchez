package com.dwolla.tracing.testkit

import cats.data.*
import cats.effect.*
import cats.effect.std.*
import cats.syntax.all.*
import com.dwolla.tracing.*
import munit.*
import munit.catseffect.*
import natchez.*
import natchez.InMemory.{Lineage, NatchezCommand}
import org.typelevel.log4cats.*

trait EnvironmentEnabledOTelTracing extends CatsEffectSuite {
  val testClassName: String
  val serviceVersion: String
  implicit val loggerFactory: LoggerFactory[IO]

  private val otelFixture: IOFixture[Option[(EntryPoint[IO], Option[IO[Chain[(Lineage, NatchezCommand)]]])]] =
    ResourceSuiteLocalFixture(
      "OpenTelemetryAtDwolla",
      Env[IO]
        .get("OTEL_EXPORTER_OTLP_ENDPOINT")
        .toResource
        .flatMap {
          _.traverse { _ =>
            OpenTelemetryAtDwolla[IO](
              testClassName,
              serviceVersion,
              DwollaEnvironment.Local,
            )
              .evalTap(_ => IO.println("using real tracing"))
              .tupleRight(none[IO[Chain[(Lineage, NatchezCommand)]]])
          }
        }
    )

  /**
   * Provides an [[EntryPoint]] for distributed tracing using OpenTelemetry if configured,
   * or an in-memory implementation otherwise. This enables tracing for monitoring and debugging purposes.
   *
   * Enable OpenTelemetry by setting the `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable
   * on the process running the tests. For example:
   *
   * {{{
   * OTEL_EXPORTER_OTLP_ENDPOINT=http://10.200.10.1:4317
   * }}}
   *
   * @return An IO effect resolving to a tuple containing an EntryPoint instance for IO and
   *         an optional IO action that provides a chain of lineage and Natchez commands when
   *         full-blown OpenTelemetry is _not_ enabled.
   */
  def entryPoint: IO[(EntryPoint[IO], Option[IO[Chain[(Lineage, NatchezCommand)]]])] = otelFixture()
    .map(_.pure[IO])
    .getOrElse(InMemory.EntryPoint.create[IO].fproduct(_.ref.get.some))

  override def munitFixtures: Seq[AnyFixture[_]] =
    super.munitFixtures ++ Seq(otelFixture)
}
