package com.dwolla.tracing

import cats.effect.*
import cats.effect.std.{Dispatcher, Random}
import cats.syntax.all.*
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.`export`.{BatchSpanProcessor, SpanExporter}
import io.opentelemetry.sdk.trace.data.SpanData
import munit.CatsEffectSuite
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import natchez.{EntryPoint, Kernel}
import org.typelevel.ci.*
import org.typelevel.log4cats.testing.TestingLogger

import java.util.concurrent.TimeUnit.HOURS

import scala.jdk.CollectionConverters.*

class OpenTelemetryAtDwollaSuite extends CatsEffectSuite with NoInheritedOtelEnvironment {
  private type Customizer = AutoConfiguredOpenTelemetrySdkBuilder => AutoConfiguredOpenTelemetrySdkBuilder

  private def entryPoint(customizers: List[Customizer],
                         env: DwollaEnvironment = DwollaEnvironment.DevInt,
                         version: Option[String] = Option("1.2.3")): Resource[IO, EntryPoint[IO]] =
    OpenTelemetryAtDwolla.buildOtel[IO]("foo-service", env, None, None, version, None, customizers)

  // A properties supplier added after Dwolla's outranks Dwolla's defaults, as an OTEL_* environment variable or
  // otel.* system property would, and Dwolla's properties customizers see it, as they would the environment.
  private def asIfFromEnvironment(properties: Map[String, String]): Customizer =
    _.addPropertiesSupplier(() => properties.asJava)

  private def recordSpan(tracing: InMemoryTracing, entryPoint: Resource[IO, EntryPoint[IO]], spanName: String): IO[List[SpanData]] =
    entryPoint.use(_.root(spanName).use_ >> tracing.finishedSpans)

  private def onlySpan(spans: List[SpanData]): SpanData =
    spans match {
      case List(span) => span
      case other => fail(s"expected one span, got $other")
    }

  test("the default resource carries service.name, service.version, and deployment.environment.name, with Prod labeled Production") {
    val tracing = InMemoryTracing()
    recordSpan(tracing, entryPoint(tracing.customizers(), env = DwollaEnvironment.Prod), "resource-span")
      .map { spans =>
        val resource = onlySpan(spans).getResource
        assertEquals(resource.getAttribute(stringKey("service.name")), "foo-service")
        assertEquals(resource.getAttribute(stringKey("service.version")), "1.2.3")
        assertEquals(resource.getAttribute(stringKey("deployment.environment.name")), "Production")
        assertEquals(resource.getAttribute(stringKey("telemetry.sdk.language")), "java")
      }
  }

  test("service.version is absent when no version is given") {
    val tracing = InMemoryTracing()
    recordSpan(tracing, entryPoint(tracing.customizers(), version = None), "no-version-span")
      .map(spans => assertEquals(Option(onlySpan(spans).getResource.getAttribute(stringKey("service.version"))), None))
  }

  test("configured resource attributes and service name win over Dwolla's") {
    val tracing = InMemoryTracing()
    val configured = asIfFromEnvironment(Map(
      "otel.service.name" -> "configured-service",
      "otel.resource.attributes" -> "deployment.environment.name=configured-env,service.version=9.9.9",
    ))
    recordSpan(tracing, entryPoint(configured :: tracing.customizers()), "configured-resource-span")
      .map { spans =>
        val resource = onlySpan(spans).getResource
        assertEquals(resource.getAttribute(stringKey("service.name")), "configured-service")
        assertEquals(resource.getAttribute(stringKey("service.version")), "9.9.9")
        assertEquals(resource.getAttribute(stringKey("deployment.environment.name")), "configured-env")
      }
  }

  test("service.name in otel.resource.attributes wins over the given service name") {
    val tracing = InMemoryTracing()
    recordSpan(tracing, entryPoint(asIfFromEnvironment(Map("otel.resource.attributes" -> "service.name=attributes-service")) :: tracing.customizers()), "attributes-service-span")
      .map(spans => assertEquals(onlySpan(spans).getResource.getAttribute(stringKey("service.name")), "attributes-service"))
  }

  test("otel.resource.disabled.keys removes Dwolla's service.version and deployment.environment.name") {
    val tracing = InMemoryTracing()
    val disabled = asIfFromEnvironment(Map("otel.resource.disabled.keys" -> "service.version,deployment.environment.name"))
    recordSpan(tracing, entryPoint(disabled :: tracing.customizers()), "disabled-dwolla-attributes-span")
      .map { spans =>
        val resource = onlySpan(spans).getResource
        assertEquals(Option(resource.getAttribute(stringKey("service.version"))), None)
        assertEquals(Option(resource.getAttribute(stringKey("deployment.environment.name"))), None)
        assertEquals(resource.getAttribute(stringKey("service.name")), "foo-service")
      }
  }

  test("otel.resource.disabled.keys removes Dwolla's service.name") {
    // the SDK's default resource may still supply its own service.name; Dwolla's must not come back
    val tracing = InMemoryTracing()
    val disabled = asIfFromEnvironment(Map("otel.resource.disabled.keys" -> "service.name"))
    recordSpan(tracing, entryPoint(disabled :: tracing.customizers()), "disabled-service-name-span")
      .map(spans => assertNotEquals(Option(onlySpan(spans).getResource.getAttribute(stringKey("service.name"))), Option("foo-service")))
  }

  test("spans keep natchez-opentelemetry's instrumentation scope name") {
    val tracing = InMemoryTracing()
    recordSpan(tracing, entryPoint(tracing.customizers()), "scope-span")
      .map(spans => assertEquals(onlySpan(spans).getInstrumentationScopeInfo.getName, "natchez.opentelemetry"))
  }

  test("with the X-Ray ID generator, a trace ID's first 8 hex digits are the epoch seconds") {
    val tracing = InMemoryTracing()
    val withXrayIds =
      (Dispatcher.parallel[IO](await = true), Random.scalaUtilRandomSeedLong[IO](20261012L).toResource).tupled.flatMap { case (dispatcher, random) =>
        implicit val r: Random[IO] = random
        OpenTelemetryAtDwolla.buildOtel[IO]("foo-service", DwollaEnvironment.Local, None, Option(AwsXrayIdGenerator[IO](dispatcher)), None, None, tracing.customizers())
      }
    for {
      before <- IO.realTime
      spans <- recordSpan(tracing, withXrayIds, "x-ray-span")
      after <- IO.realTime
    } yield {
      val traceId = onlySpan(spans).getTraceId
      val epochSeconds = java.lang.Long.parseLong(traceId.take(8), 16)
      assert(epochSeconds >= before.toSeconds && epochSeconds <= after.toSeconds, s"$traceId vs [$before, $after]")
    }
  }

  test("the global propagators are W3C trace context, B3 multi-header, and X-Ray") {
    entryPoint(InMemoryTracing().customizers())
      .use(_ => IO(GlobalOpenTelemetry.getPropagators.getTextMapPropagator.fields.asScala.toSet))
      .map { fields =>
        assert(Set("traceparent", "X-B3-TraceId", "X-Amzn-Trace-Id").subsetOf(fields), fields)
      }
  }

  test("Dwolla's defaults reach autoconfigure") {
    InMemoryTracing.resolvedConfig(capture => entryPoint(InMemoryTracing().customizers() :+ capture))
      .map { config =>
        assertEquals(config.getString("otel.metrics.exporter"), "none")
        assertEquals(config.getString("otel.logs.exporter"), "none")
        assertEquals(config.getString("otel.exporter.otlp.compression"), "gzip")
        assertEquals(config.getString("otel.exporter.otlp.protocol"), "grpc")
        assertEquals(config.getString("otel.bsp.max.export.batch.size"), "128")
        assertEquals(config.getString("otel.propagators"), "tracecontext,b3multi,xray")
      }
  }

  test("metrics and logs exporters stay off even when the environment or system properties configure them") {
    // e.g. meant for an OtelJavaAtDwolla metrics SDK in the same JVM
    val configuredElsewhere = asIfFromEnvironment(Map("otel.metrics.exporter" -> "otlp", "otel.logs.exporter" -> "otlp"))
    InMemoryTracing.resolvedConfig(capture => entryPoint((configuredElsewhere :: InMemoryTracing().customizers()) :+ capture))
      .map { config =>
        assertEquals(config.getString("otel.metrics.exporter"), "none")
        assertEquals(config.getString("otel.logs.exporter"), "none")
      }
  }

  test("the configured sampler is honored") {
    val tracing = InMemoryTracing()
    recordSpan(tracing, entryPoint(tracing.customizers(Map("otel.traces.sampler" -> "always_off"))), "unsampled-span")
      .map(spans => assertEquals(spans, Nil))
  }

  test("a parent-based ratio sampler drops unsampled roots, but children follow a sampled remote parent") {
    val tracing = InMemoryTracing()
    val sampler = Map("otel.traces.sampler" -> "parentbased_traceidratio", "otel.traces.sampler.arg" -> "0.0")
    val sampledParent = Kernel(Map(ci"traceparent" -> "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"))
    entryPoint(tracing.customizers(sampler))
      .use(ep => ep.root("unsampled-root").use_ >> ep.continue("child-of-sampled-parent", sampledParent).use_ >> tracing.finishedSpans)
      .map { spans =>
        assertEquals(spans.map(_.getName), List("child-of-sampled-parent"))
        assertEquals(spans.map(_.getTraceId), List("0af7651916cd43dd8448eb211c80319c"))
      }
  }

  test("the logging span processor, when given, receives spans") {
    val tracing = InMemoryTracing()
    val logged = InMemorySpanExporter.create()
    OpenTelemetryAtDwolla.buildOtel[IO]("foo-service", DwollaEnvironment.DevInt, Option(SimpleSpanProcessor.create(logged)), None, None, None, tracing.customizers())
      .use(_.root("logged-span").use_ >> IO(logged.getFinishedSpanItems.asScala.toList.map(_.getName)))
      .map(names => assertEquals(names, List("logged-span")))
  }

  test("an application's sampler, registered through autoconfigure's SPI, is loaded by name") {
    val tracing = InMemoryTracing()
    recordSpan(tracing, entryPoint(tracing.customizers(Map("otel.traces.sampler" -> DropAllSamplerProvider.name))), "dropped-span")
      .map(spans => assertEquals(spans, Nil))
  }

  test("release flushes pending spans and unregisters the global SDK, so another can start") {
    val spans = InMemorySpanExporter.create()
    // keeps its spans after the SDK shuts down, so they can be read after release
    val retaining: SpanExporter = new SpanExporter {
      override def `export`(batch: java.util.Collection[SpanData]): CompletableResultCode = spans.`export`(batch)
      override def flush(): CompletableResultCode = spans.flush()
      override def shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
    }
    // only a flush exports before the hour-long schedule delay
    val batching: Customizer =
      _.addPropertiesCustomizer((_: ConfigProperties) => Map("otel.traces.exporter" -> "none").asJava)
        .addTracerProviderCustomizer { (tracerProvider: SdkTracerProviderBuilder, _: ConfigProperties) =>
          tracerProvider.addSpanProcessor(BatchSpanProcessor.builder(retaining).setScheduleDelay(1L, HOURS).build())
        }

    val second = InMemoryTracing()
    for {
      pendingAtRelease <- entryPoint(List(batching)).use(_.root("pending-span").use_ >> IO(spans.getFinishedSpanItems.size))
      flushed <- IO(spans.getFinishedSpanItems.asScala.toList.map(_.getName))
      secondSpans <- recordSpan(second, entryPoint(second.customizers()), "second-sdk-span")
    } yield {
      assertEquals(pendingAtRelease, 0)
      assertEquals(flushed, List("pending-span"))
      assertEquals(secondSpans.map(_.getName), List("second-sdk-span"))
    }
  }

  test("release warns when the SDK doesn't shut down cleanly, and still unregisters the global SDK") {
    val failsToShutDown: SpanExporter = new SpanExporter {
      override def `export`(batch: java.util.Collection[SpanData]): CompletableResultCode = CompletableResultCode.ofSuccess()
      override def flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
      override def shutdown(): CompletableResultCode = CompletableResultCode.ofFailure()
    }
    val failingShutdown: Customizer =
      _.addTracerProviderCustomizer { (tracerProvider: SdkTracerProviderBuilder, _: ConfigProperties) =>
        tracerProvider.addSpanProcessor(SimpleSpanProcessor.create(failsToShutDown))
      }
    val logger = TestingLogger.atomic[IO]()

    val second = InMemoryTracing()
    for {
      _ <- OpenTelemetryAtDwolla.buildOtel[IO]("foo-service", DwollaEnvironment.DevInt, None, None, None, logger.some, InMemoryTracing().customizers() :+ failingShutdown).use_
      logged <- logger.logged
      secondSpans <- recordSpan(second, entryPoint(second.customizers()), "second-sdk-span")
    } yield {
      assertEquals(logged, Vector(TestingLogger.WARN("OpenTelemetry SDK shutdown failed or didn't finish within 10 seconds; some spans may not have been exported", None)))
      assertEquals(secondSpans.map(_.getName), List("second-sdk-span"))
    }
  }

  test("buildOtel works for a Sync-only F (the deprecated apply's non-Async branch)") {
    // a Sync that isn't Async takes the deprecated apply's non-Async branch, which calls buildOtel without an
    // X-Ray ID generator or span logging
    val tracing = InMemoryTracing()
    IO {
      OpenTelemetryAtDwolla.buildOtel[SyncIO]("foo-service", DwollaEnvironment.DevInt, None, None, None, None, tracing.customizers())
        .use(_.root("sync-span").use_ >> SyncIO(tracing.spans.getFinishedSpanItems.asScala.toList.map(_.getName)))
        .unsafeRunSync()
    }.map(names => assertEquals(names, List("sync-span")))
  }
}
