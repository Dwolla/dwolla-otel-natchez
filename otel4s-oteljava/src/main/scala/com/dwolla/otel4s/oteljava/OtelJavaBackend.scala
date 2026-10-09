package com.dwolla.otel4s.oteljava

import cats.effect.*
import cats.effect.std.{Dispatcher, Random, SecureRandom, UUIDGen}
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.dwolla.otel4s.*
import com.dwolla.tracing.{AwsXrayIdGenerator, LoggingSpanExporter, ResourceAttributeNames}
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.resources.Resource as OTResource
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import org.typelevel.log4cats.Logger
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.oteljava.context.LocalContextProvider
import org.typelevel.otel4s.trace.TracerProvider

import java.util.UUID
import scala.jdk.CollectionConverters.*

/** Starts an OpenTelemetry Java SDK through `OtelJava.autoConfigured`. */
final class OtelJavaBackend[F[_] : Async : LocalContextProvider : Random] private (
  private[oteljava] val globalRegistration: Boolean,
  private[oteljava] val startupHooks: List[OpenTelemetry => Resource[F, Unit]],
  private[oteljava] val autoConfigureCustomizers: List[AutoConfiguredOpenTelemetrySdkBuilder => AutoConfiguredOpenTelemetrySdkBuilder],
) extends Backend[F] {

  override def start(settings: OtelAtDwollaSettings[F]): Resource[F, (TracerProvider[F], MeterProvider[F])] =
    for {
      dispatcher <- Dispatcher.parallel[F](await = true)
      spanLogger <- settings.spanLogging.traverse(_.fromName("com.dwolla.otel4s.oteljava.LoggedSpans").toResource)
      instanceId <- SecureRandom.javaSecuritySecureRandom[F].flatMap { implicit secureRandom =>
        UUIDGen.fromSecureRandom[F].randomUUID
      }.toResource
      otelJava <- OtelJava.autoConfigured[F](configure(settings, instanceId, dispatcher, spanLogger))
      // acquired after the SDK, so released before it shuts down
      _ <- startupHooks.traverse_(_(otelJava.underlying))
    } yield (otelJava.tracerProvider, otelJava.meterProvider)

  private def configure(settings: OtelAtDwollaSettings[F], instanceId: UUID, dispatcher: Dispatcher[F], spanLogger: Option[Logger[F]])
                       (builder: AutoConfiguredOpenTelemetrySdkBuilder): AutoConfiguredOpenTelemetrySdkBuilder = {
    val withDefaults =
      builder
        .addPropertiesSupplier(() => DwollaDefaults.properties(settings).asJava)
        .addPropertiesCustomizer { (config: ConfigProperties) =>
          DwollaDefaults.serviceName(
            settings,
            Option(config.getString("otel.service.name")),
            config.getMap("otel.resource.attributes").asScala.toMap,
          ).asJava
        }
        .addResourceCustomizer { (configured: OTResource, _: ConfigProperties) =>
          // configured attributes win: Dwolla's only fill in keys that are missing
          dwollaResource(settings, instanceId).merge(configured)
        }
    val withTracing =
      if (settings.tracingEnabled)
        withDefaults.addTracerProviderCustomizer { (tracerProvider: SdkTracerProviderBuilder, _: ConfigProperties) =>
          spanLogger.foldLeft(tracerProvider.setIdGenerator(AwsXrayIdGenerator(dispatcher))) { (b, logger) =>
            implicit val l: Logger[F] = logger
            b.addSpanProcessor(SimpleSpanProcessor.create(new LoggingSpanExporter[F](dispatcher)))
          }
        }
      else withDefaults
    val withRegistration = if (globalRegistration) withTracing.setResultAsGlobal() else withTracing
    autoConfigureCustomizers.foldLeft(withRegistration)((b, customize) => customize(b))
  }

  // a random UUIDv4 per SDK start, as semantic conventions recommend: without one, every instance of a service
  // writes the same cumulative metric streams
  private def dwollaResource(settings: OtelAtDwollaSettings[F], instanceId: UUID): OTResource =
    OTResource.create(
      Attributes.builder()
        .put(stringKey(ResourceAttributeNames.serviceVersion), settings.serviceVersion)
        .put(stringKey(ResourceAttributeNames.serviceInstanceId), instanceId.toString)
        .put(stringKey(ResourceAttributeNames.deploymentEnvironmentName), settings.environment.deploymentEnvironmentName)
        .build()
    )

  private[oteljava] def copy(globalRegistration: Boolean = globalRegistration,
                             startupHooks: List[OpenTelemetry => Resource[F, Unit]] = startupHooks,
                             autoConfigureCustomizers: List[AutoConfiguredOpenTelemetrySdkBuilder => AutoConfiguredOpenTelemetrySdkBuilder] = autoConfigureCustomizers,
                            ): OtelJavaBackend[F] =
    new OtelJavaBackend[F](globalRegistration, startupHooks, autoConfigureCustomizers)
}

object OtelJavaBackend {
  private[oteljava] def apply[F[_] : Async : LocalContextProvider : Random]: OtelJavaBackend[F] =
    new OtelJavaBackend[F](globalRegistration = false, startupHooks = Nil, autoConfigureCustomizers = Nil)

  /** OpenTelemetry-Java-only builder options. In implicit scope through the builder's backend type: no import needed. */
  implicit class OtelJavaBuilderOps[F[_], E](private val builder: OtelAtDwollaBuilder[F, OtelJavaBackend[F], E]) extends AnyVal {
    /**
     * Also register the SDK as `GlobalOpenTelemetry`. Acquisition fails if anything already registered one, or
     * if anything already called `GlobalOpenTelemetry.get()` (which installs a no-op on its first call).
     */
    def registerGlobally: OtelAtDwollaBuilder[F, OtelJavaBackend[F], E] =
      builder.mapBackend(_.copy(globalRegistration = true))

    /** Acquire `hook` once the SDK has started; it is released before the SDK shuts down. */
    def withStartupHook(hook: OpenTelemetry => Resource[F, Unit]): OtelAtDwollaBuilder[F, OtelJavaBackend[F], E] =
      builder.mapBackend(b => b.copy(startupHooks = b.startupHooks :+ hook))

    private[otel4s] def withAutoConfigureCustomizer(customize: AutoConfiguredOpenTelemetrySdkBuilder => AutoConfiguredOpenTelemetrySdkBuilder): OtelAtDwollaBuilder[F, OtelJavaBackend[F], E] =
      builder.mapBackend(b => b.copy(autoConfigureCustomizers = b.autoConfigureCustomizers :+ customize))
  }
}
