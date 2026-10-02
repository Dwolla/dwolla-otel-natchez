package com.dwolla.otel4s.oteljava

import cats.effect.*
import cats.effect.std.Random
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.GenericContainer.FileSystemBind
import com.dwolla.tracing.DwollaEnvironment
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import munit.{AnyFixture, CatsEffectSuite}
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.wait.strategy.Wait

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/**
 * Exports with Dwolla's defaults through the real OTLP/gRPC exporter to a collector in Docker, then reads its
 * debug exporter's logs. Polls on the wall clock because the collector is an external process.
 */
class EndToEndSuite extends CatsEffectSuite {
  private val collector = ResourceSuiteLocalFixture(
    "otel-collector",
    Resource.make(IO.blocking {
      val container = GenericContainer(
        dockerImage = "otel/opentelemetry-collector:0.162.0",
        exposedPorts = Seq(4317),
        classpathResourceMapping = Seq(FileSystemBind("otel-collector-config.yaml", "/etc/otelcol/config.yaml", BindMode.READ_ONLY)),
        waitStrategy = Wait.forLogMessage(".*Everything is ready.*", 1),
      )
      container.start()
      container
    })(container => IO.blocking(container.stop()))
  )

  override def munitFixtures: Seq[AnyFixture[_]] = List(collector)

  // The debug exporter logs each signal's batch as one entry that starts "info<TAB>ResourceSpans #" or
  // "info<TAB>ResourceMetrics #" and runs until the next log entry.
  private def section(logs: String, signal: String): String =
    logs.split("\tinfo\t").filter(_.startsWith(signal)).mkString("\n")

  private def logsEventuallyContain(container: GenericContainer, expected: Map[String, List[String]], attemptsLeft: Int = 40): IO[Unit] =
    IO.blocking(container.logs).flatMap { logs =>
      val missing = expected.toList.flatMap { case (signal, facts) =>
        val signalLogs = section(logs, signal)
        facts.filterNot(signalLogs.contains).map(fact => s"$signal: $fact")
      }
      if (missing.isEmpty) IO.unit
      else if (attemptsLeft <= 0) IO(fail(s"collector never logged ${missing.mkString(", ")}", clues(logs)))
      else IO.sleep(500.millis) >> logsEventuallyContain(container, expected, attemptsLeft - 1)
    }

  private val resourceAttributes = List(
    "service.name: Str(e2e-service)",
    "service.version: Str(1.2.3)",
    "deployment.environment.name: Str(Local)",
  )

  test("spans and metrics recorded just before release reach the collector with Dwolla's resource attributes") {
    val container = collector()
    val endpoint = s"http://${container.host}:${container.mappedPort(4317)}"
    Random.scalaUtilRandomSeedLong[IO](20261002L).flatMap { implicit random =>
      OtelJavaBackend.OtelJavaBuilderOps(
        OtelJavaAtDwolla[IO]("e2e-service", "1.2.3", DwollaEnvironment.Local)
          .withTracing
          .withMetrics
      )
        .withAutoConfigureCustomizer(_.addPropertiesCustomizer((_: ConfigProperties) => Map("otel.exporter.otlp.endpoint" -> endpoint).asJava))
        .build
        .use { case (tracerProvider, meterProvider) =>
          tracerProvider.get("e2e").flatMap(_.span("e2e-span").use_) >>
            meterProvider.get("e2e").flatMap(_.counter[Long]("e2e.requests").create.flatMap(_.add(1L)))
        } >>
        logsEventuallyContain(container, Map(
          "ResourceSpans" -> (resourceAttributes :+ "e2e-span"),
          "ResourceMetrics" -> (resourceAttributes :+ "e2e.requests"),
        ))
    }
  }
}
