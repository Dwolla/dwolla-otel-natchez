package com.dwolla.otel4s.oteljava

import cats.effect.*
import cats.effect.std.Random
import com.dwolla.tracing.DwollaEnvironment
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.{Span, SpanContext}
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.{TextMapGetter, TextMapSetter}
import munit.CatsEffectSuite

import java.util as ju
import scala.jdk.CollectionConverters.*

private final case class Propagated(sent: SpanContext, headers: Map[String, String], roundTripped: SpanContext, fromXRayOnly: SpanContext)

/**
 * Linkage guard: the X-Ray propagator is an -alpha artifact built against an older OpenTelemetry than otelV,
 * so calling it here turns a NoClassDefFoundError or NoSuchMethodError into a build failure.
 */
class PropagationSuite extends CatsEffectSuite {
  private val setter: TextMapSetter[ju.Map[String, String]] =
    (carrier: ju.Map[String, String], key: String, value: String) => { carrier.put(key, value); () }

  private val getter: TextMapGetter[ju.Map[String, String]] = new TextMapGetter[ju.Map[String, String]] {
    override def keys(carrier: ju.Map[String, String]): java.lang.Iterable[String] = carrier.keySet
    override def get(carrier: ju.Map[String, String], key: String): String =
      if (carrier == null) null
      else carrier.asScala.collectFirst { case (k, v) if k.equalsIgnoreCase(key) => v }.orNull
  }

  // Example X-Amzn-Trace-Id value from AWS's X-Ray documentation
  private val xRayOnlyHeader = Map("X-Amzn-Trace-Id" -> "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1")

  private def propagate(otel: OpenTelemetry): Propagated = {
    val propagator = otel.getPropagators.getTextMapPropagator
    val span = otel.getTracer("propagation").spanBuilder("outgoing").startSpan()
    val carrier = new ju.HashMap[String, String]()
    try propagator.inject(Context.root().`with`(span), carrier, setter)
    finally span.end()
    val roundTripped = Span.fromContext(propagator.extract(Context.root(), carrier, getter)).getSpanContext
    val fromXRayOnly = Span.fromContext(propagator.extract(Context.root(), new ju.HashMap[String, String](xRayOnlyHeader.asJava), getter)).getSpanContext
    Propagated(span.getSpanContext, carrier.asScala.toMap, roundTripped, fromXRayOnly)
  }

  test("the default propagators inject W3C, B3 multi-header, and X-Ray headers, and extract X-Ray-only requests") {
    Random.scalaUtilRandomSeedLong[IO](20261002L).flatMap { implicit random =>
      Ref[IO].of(Option.empty[Propagated]).flatMap { result =>
        InMemoryTelemetry().attachTo(
          OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local)
            .withTracing
            .withStartupHook(otel => IO(propagate(otel)).flatMap(p => result.set(Some(p))).toResource)
        )
          .build
          .use_ >> result.get
      }
    }.map {
      case None => fail("the startup hook never ran")
      case Some(Propagated(sent, headers, roundTripped, fromXRayOnly)) =>
        val headerNames = headers.keySet.map(_.toLowerCase)
        assert(Set("traceparent", "x-b3-traceid", "x-amzn-trace-id").subsetOf(headerNames), headers)
        assertEquals(roundTripped.getTraceId, sent.getTraceId)
        assertEquals(roundTripped.getSpanId, sent.getSpanId)
        assertEquals(fromXRayOnly.getTraceId, "5759e988bd862e3fe1be46a994272793")
        assertEquals(fromXRayOnly.getSpanId, "53995c3f42cd8ad8")
    }
  }
}
