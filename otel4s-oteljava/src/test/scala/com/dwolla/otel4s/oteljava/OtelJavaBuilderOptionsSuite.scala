package com.dwolla.otel4s.oteljava

import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import com.dwolla.otel4s.{OtelAtDwollaBuilder, Signal}
import com.dwolla.tracing.DwollaEnvironment
import io.opentelemetry.api.{GlobalOpenTelemetry, OpenTelemetry}
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder
import io.opentelemetry.sdk.metrics.{Aggregation, ExplicitBucketHistogramOptions, InstrumentSelector, View}
import io.opentelemetry.sdk.metrics.data.MetricData
import munit.CatsEffectSuite
import org.typelevel.log4cats.testing.TestingLoggerFactory
import org.typelevel.otel4s.metrics.BucketBoundaries

import scala.jdk.CollectionConverters.*

class OtelJavaBuilderOptionsSuite extends CatsEffectSuite with NoInheritedOtelEnvironment {
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

  private val advisedBoundaries = Vector(0.005, 0.01, 0.025)
  private val viewBoundaries = List(0.005, 0.0075, 0.01, 0.0125, 0.015, 0.0175, 0.02, 0.025)

  private def selectByName(name: String, boundaries: List[Double]): (InstrumentSelector, View) =
    InstrumentSelector.builder().setName(name).build() ->
      View.builder().setAggregation(Aggregation.explicitBucketHistogram(ExplicitBucketHistogramOptions.builder().setBucketBoundaries(boundaries.map(Double.box).asJava).build())).build()

  // records one value in each named histogram, all created with the advised boundaries, and returns their bucket boundaries
  private def histogramBoundaries(builder: InMemoryTelemetry => OtelAtDwollaBuilder[IO, OtelJavaBackend[IO], Any with Signal.Metrics],
                                  names: String*): IO[Map[String, List[Double]]] = {
    val telemetry = InMemoryTelemetry()
    builder(telemetry)
      .build
      .use { meterProvider =>
        meterProvider.get("test").flatMap { meter =>
          names.toList.traverse_ { name =>
            meter.histogram[Double](name).withExplicitBucketBoundaries(BucketBoundaries(advisedBoundaries)).create.flatMap(_.record(0.007))
          }
        } >> IO(telemetry.metrics.collectAllMetrics().asScala.toList)
      }
      .map(_.map { (metric: MetricData) =>
        metric.getName -> metric.getHistogramData.getPoints.asScala.head.getBoundaries.asScala.map(_.doubleValue).toList
      }.toMap)
  }

  private def metricsBuilder(implicit random: Random[IO]) = OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withMetrics

  test("withView replaces the advised boundaries of the histogram it selects and leaves the others alone") {
    withRandom { implicit random =>
      val (selector, view) = selectByName("selected.duration", viewBoundaries)
      histogramBoundaries(t => t.attachTo(metricsBuilder.withView(selector, view)), "selected.duration", "other.duration")
        .map(assertEquals(_, Map("selected.duration" -> viewBoundaries, "other.duration" -> advisedBoundaries.toList)))
    }
  }

  private def exportedAttributeKeys(builder: InMemoryTelemetry => OtelAtDwollaBuilder[IO, OtelJavaBackend[IO], Any with Signal.Metrics]): IO[Set[String]] = {
    val telemetry = InMemoryTelemetry()
    val recordWithAdvisedKeys: OpenTelemetry => Resource[IO, Unit] = otel =>
      IO {
        otel.getMeter("test")
          .histogramBuilder("advised.duration")
          .asInstanceOf[ExtendedDoubleHistogramBuilder]
          .setAttributesAdvice(List[AttributeKey[?]](stringKey("advised")).asJava)
          .build()
          .record(0.007, Attributes.of(stringKey("advised"), "a", stringKey("extra"), "b"))
      }.toResource
    builder(telemetry).withStartupHook(recordWithAdvisedKeys)
      .build
      .use(_ => IO(telemetry.metrics.collectAllMetrics().asScala.toList))
      .map(_.flatMap(_.getHistogramData.getPoints.asScala).flatMap(_.getAttributes.asMap.keySet.asScala).map(_.getKey).toSet)
  }

  test("a View that selects an instrument replaces its advised attribute keys, so every recorded attribute is exported") {
    withRandom { implicit random =>
      val (selector, view) = selectByName("advised.duration", viewBoundaries)
      for {
        withoutView <- exportedAttributeKeys(t => t.attachTo(metricsBuilder))
        withView <- exportedAttributeKeys(t => t.attachTo(metricsBuilder.withView(selector, view)))
      } yield {
        assertEquals(withoutView, Set("advised"))
        assertEquals(withView, Set("advised", "extra"))
      }
    }
  }

  test("repeated withView calls each register their View") {
    withRandom { implicit random =>
      val (firstSelector, firstView) = selectByName("first.duration", viewBoundaries)
      val (secondSelector, secondView) = selectByName("second.duration", List(1.0, 2.0))
      histogramBoundaries(t => t.attachTo(metricsBuilder.withView(firstSelector, firstView).withView(secondSelector, secondView)), "first.duration", "second.duration")
        .map(assertEquals(_, Map("first.duration" -> viewBoundaries, "second.duration" -> List(1.0, 2.0))))
    }
  }

  test("withView requires withMetrics") {
    val errors = compileErrors(
      """def tracingOnly(implicit random: Random[IO]) = OtelJavaAtDwolla[IO]("foo-service", "1.2.3", DwollaEnvironment.Local).withTracing.withView(InstrumentSelector.builder().build(), View.builder().build())"""
    )
    assert(errors.contains("Signal.Metrics"), errors)
  }
}
