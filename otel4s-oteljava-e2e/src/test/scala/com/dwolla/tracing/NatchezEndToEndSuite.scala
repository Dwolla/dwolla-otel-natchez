package com.dwolla.tracing

import cats.effect.*
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.GenericContainer.FileSystemBind
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import munit.{AnyFixture, CatsEffectSuite}
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.wait.strategy.Wait

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/**
 * Exports a natchez span through `OpenTelemetryAtDwolla`'s real OTLP/gRPC exporter to a collector in Docker, then
 * reads its debug exporter's logs. Polls on the wall clock because the collector is an external process.
 */
class NatchezEndToEndSuite extends CatsEffectSuite {
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

  private def logsEventuallyContain(container: GenericContainer, expected: List[String], attemptsLeft: Int = 40): IO[Unit] =
    IO.blocking(container.logs).flatMap { logs =>
      val missing = expected.filterNot(logs.contains)
      if (missing.isEmpty) IO.unit
      else if (attemptsLeft <= 0) IO(fail(s"collector never logged ${missing.mkString(", ")}", clues(logs)))
      else IO.sleep(500.millis) >> logsEventuallyContain(container, expected, attemptsLeft - 1)
    }

  test("a span recorded just before the EntryPoint is released reaches the collector with Dwolla's resource attributes") {
    val container = collector()
    val endpoint = s"http://${container.host}:${container.mappedPort(4317)}"
    val pointAtCollector: AutoConfiguredCustomizer =
      _.addPropertiesCustomizer((_: ConfigProperties) => Map("otel.exporter.otlp.endpoint" -> endpoint, "otel.bsp.schedule.delay" -> "3600000").asJava)

    OpenTelemetryAtDwolla.buildOtel[IO]("e2e-service", DwollaEnvironment.Local, None, None, Option("1.2.3"), None, List(pointAtCollector))
      .use(_.root("natchez-e2e-span").use_) >>
      logsEventuallyContain(container, List(
        "service.name: Str(e2e-service)",
        "service.version: Str(1.2.3)",
        "deployment.environment.name: Str(Local)",
        "natchez-e2e-span",
      ))
  }

  private type AutoConfiguredCustomizer =
    io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder => io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder
}
