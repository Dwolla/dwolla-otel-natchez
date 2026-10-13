package com.dwolla.tracing

import cats.effect.std.*
import cats.effect.syntax.all.*
import cats.effect.{Trace as _, *}
import cats.syntax.all.*
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.{AutoConfiguredOpenTelemetrySdk, AutoConfiguredOpenTelemetrySdkBuilder}
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.resources.Resource as OTResource
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.{SdkTracerProviderBuilder, SpanProcessor}
import natchez.*
import natchez.opentelemetry.OpenTelemetry
import org.typelevel.log4cats.{Logger, LoggerFactory}

import java.util.concurrent.TimeUnit.SECONDS

import scala.annotation.unused
import scala.jdk.CollectionConverters.*

object OpenTelemetryAtDwolla {
  def apply[F[_] : Async : LoggerFactory : Random](serviceName: String,
                                                   serviceVersion: String,
                                                   env: DwollaEnvironment): Resource[F, EntryPoint[F]] =
    entryPoint(serviceName, serviceVersion.some, env, logTraces = false)

  def apply[F[_] : Async : LoggerFactory : Random](serviceName: String,
                                                   serviceVersion: String,
                                                   env: DwollaEnvironment,
                                                   logTraces: Boolean): Resource[F, EntryPoint[F]] =
    entryPoint(serviceName, serviceVersion.some, env, logTraces)

  def apply[F[_] : Async : LoggerFactory : Random](serviceName: String,
                                                   serviceVersion: Option[String],
                                                   env: DwollaEnvironment,
                                                   logTraces: Boolean,
                                                   dispatcher: Dispatcher[F]): Resource[F, EntryPoint[F]] =
    entryPoint(serviceName, serviceVersion, env, logTraces, dispatcher)

  private def entryPoint[F[_] : Async : LoggerFactory : Random](serviceName: String,
                                                                serviceVersion: Option[String],
                                                                env: DwollaEnvironment,
                                                                logTraces: Boolean): Resource[F, EntryPoint[F]] =
    Dispatcher.parallel(await = true)
      .flatMap(entryPoint(serviceName, serviceVersion, env, logTraces, _))

  private def entryPoint[F[_] : Async : LoggerFactory : Random](serviceName: String,
                                                                serviceVersion: Option[String],
                                                                env: DwollaEnvironment,
                                                                logTraces: Boolean,
                                                                dispatcher: Dispatcher[F]): Resource[F, EntryPoint[F]] =
    LoggerFactory[F]
      .create
      .toResource
      .flatMap { implicit logger =>
        val loggingProcessor = logTraces.guard[Option].map(_ => SimpleSpanProcessor.create(new LoggingSpanExporter(dispatcher)))
        buildOtel(serviceName, env, loggingProcessor, AwsXrayIdGenerator(dispatcher).some, serviceVersion, logger.some, Nil)
      }

  private[tracing] def resourceAttributes(serviceName: String,
                                          env: DwollaEnvironment,
                                          version: Option[String]): Attributes =
    version.foldl {
      Attributes.builder()
        .put(OtelAttributes.serviceName, serviceName)
        .put(OtelAttributes.deploymentEnvironmentName, env.deploymentEnvironmentName)
    }(_.put(OtelAttributes.serviceVersion, _))
      .build()

  /**
   * `logger`, when given, warns if the SDK can't flush and shut down cleanly on release. `autoConfigureCustomizers`
   * are applied after Dwolla's, so tests can observe and override the configuration.
   */
  private[tracing] def buildOtel[F[_] : Sync](serviceName: String,
                                              env: DwollaEnvironment,
                                              loggingProcessor: Option[SpanProcessor],
                                              awsXrayIdGenerator: Option[AwsXrayIdGenerator[F]],
                                              version: Option[String],
                                              logger: Option[Logger[F]],
                                              autoConfigureCustomizers: List[AutoConfiguredOpenTelemetrySdkBuilder => AutoConfiguredOpenTelemetrySdkBuilder],
                                             ): Resource[F, EntryPoint[F]] = {
    // the builder is mutable, so each acquisition gets its own
    def configure(): AutoConfiguredOpenTelemetrySdkBuilder =
      AutoConfiguredOpenTelemetrySdk.builder()
        .addPropertiesSupplier(() => OtelDefaultProperties.properties(tracingEnabled = true, metricsEnabled = false).asJava)
        // forced, not defaulted: customizers outrank OTEL_* environment variables and otel.* system properties,
        // which may be meant for a metrics SDK in the same JVM
        .addPropertiesCustomizer((_: ConfigProperties) => Map("otel.metrics.exporter" -> "none", "otel.logs.exporter" -> "none").asJava)
        .addPropertiesCustomizer { (config: ConfigProperties) =>
          OtelDefaultProperties.serviceName(
            serviceName,
            Option(config.getString("otel.service.name")),
            config.getMap("otel.resource.attributes").asScala.toMap,
          ).asJava
        }
        .addResourceCustomizer { (configured: OTResource, config: ConfigProperties) =>
          // configured attributes win: Dwolla's only fill in keys that are missing. Autoconfigure removes
          // otel.resource.disabled.keys before customizers run, so Dwolla's must drop them too, or the merge restores them.
          val disabledKeys = config.getList("otel.resource.disabled.keys").asScala.toSet
          OTResource.create(resourceAttributes(serviceName, env, version))
            .toBuilder
            .removeIf(key => disabledKeys.contains(key.getKey))
            .build()
            .merge(configured)
        }
        .addTracerProviderCustomizer { (tracerProvider: SdkTracerProviderBuilder, _: ConfigProperties) =>
          loggingProcessor.foldl {
            awsXrayIdGenerator.foldl(tracerProvider)(_ setIdGenerator _)
          }(_ addSpanProcessor _)
        }
        .setResultAsGlobal()
        // the Resource's release owns shutdown: a JVM shutdown hook would race IOApp's finalizers on SIGTERM, shutting
        // the SDK down before spans ended during graceful shutdown are flushed, and each acquisition would leak a hook
        .disableShutdownHook()

    val acquire = Sync[F].delay {
      autoConfigureCustomizers.foldl(configure())((b, customize) => customize(b)).build().getOpenTelemetrySdk
    }

    // shutting down flushes pending spans; resetting the global lets another SDK register, e.g. in tests
    def release(sdk: OpenTelemetrySdk): F[Unit] =
      Sync[F].blocking(sdk.shutdown().join(10, SECONDS))
        .flatMap { result =>
          logger
            .traverse_(_.warn("OpenTelemetry SDK shutdown failed or didn't finish within 10 seconds; some spans may not have been exported"))
            .unlessA(result.isSuccess)
        }
        .guarantee(Sync[F].delay(GlobalOpenTelemetry.resetForTest()))

    Resource.make(acquire)(release)
      // natchez-opentelemetry's instrumentation scope name, kept from its `entryPoint`
      .map(sdk => OpenTelemetry.entryPointFor[F](sdk, sdk.getTracer("natchez.opentelemetry"), None))
  }

  @deprecated("Doesn't log. Unless F[_]: Async, doesn't generate X-Ray compatible trace IDs. Only maintained for binary compatibility.", "0.2.3")
  def apply[F[_]](serviceName: String, env: DwollaEnvironment, F: Sync[F], @unused E: Env[F]): Resource[F, EntryPoint[F]] =
    F match {
      case async: Async[F] =>
        import org.typelevel.log4cats.noop.NoOpFactory

        Random.scalaUtilRandom(F)
          .toResource
          .flatMap {
            entryPoint(serviceName, None, env, logTraces = false)(async, NoOpFactory(F), _)
          }
      case _ =>
        buildOtel(serviceName, env, None, None, None, None, Nil)(F)
    }

  @deprecated("provide service version; only maintained for binary compatibility", "0.2.3")
  def apply[F[_] : Async : LoggerFactory : Random](serviceName: String,
                                                   env: DwollaEnvironment): Resource[F, EntryPoint[F]] =
    entryPoint(serviceName, None, env, logTraces = false)
  @deprecated("provide service version; only maintained for binary compatibility", "0.2.3")
  def apply[F[_] : Async : LoggerFactory : Random](serviceName: String,
                                                   env: DwollaEnvironment,
                                                   logTraces: Boolean,
                                                   dispatcher: Dispatcher[F]): Resource[F, EntryPoint[F]] =
    entryPoint(serviceName, None, env, logTraces, dispatcher)

  @deprecated("provide service version; only maintained for binary compatibility", "0.2.3")
  def apply[F[_] : Async : LoggerFactory : Random](serviceName: String,
                                                   env: DwollaEnvironment,
                                                   logTraces: Boolean): Resource[F, EntryPoint[F]] =
    entryPoint(serviceName, None, env, logTraces)

  // binary compatibility: the Env-taking signatures, kept for code compiled against <= 0.2.9
  @deprecated("binary compatibility only: compiled code from <= 0.2.9 calls this; use the overload without Env", "0.2.10")
  def apply[F[_]](serviceName: String,
                  serviceVersion: String,
                  env: DwollaEnvironment,
                  ev1: Async[F],
                  @unused ev2: Env[F],
                  ev3: LoggerFactory[F],
                  ev4: Random[F],
                 ): Resource[F, EntryPoint[F]] =
    entryPoint(serviceName, serviceVersion.some, env, logTraces = false)(ev1, ev3, ev4)

  @deprecated("binary compatibility only: compiled code from <= 0.2.9 calls this; use the overload without Env", "0.2.10")
  def apply[F[_]](serviceName: String,
                  serviceVersion: String,
                  env: DwollaEnvironment,
                  logTraces: Boolean,
                  ev1: Async[F],
                  @unused ev2: Env[F],
                  ev3: LoggerFactory[F],
                  ev4: Random[F],
                 ): Resource[F, EntryPoint[F]] =
    entryPoint(serviceName, serviceVersion.some, env, logTraces)(ev1, ev3, ev4)

  @deprecated("binary compatibility only: compiled code from <= 0.2.9 calls this; use the overload without Env", "0.2.10")
  def apply[F[_]](serviceName: String,
                  serviceVersion: Option[String],
                  env: DwollaEnvironment,
                  logTraces: Boolean,
                  dispatcher: Dispatcher[F],
                  ev1: Async[F],
                  @unused ev2: Env[F],
                  ev3: LoggerFactory[F],
                  ev4: Random[F],
                 ): Resource[F, EntryPoint[F]] =
    entryPoint(serviceName, serviceVersion, env, logTraces, dispatcher)(ev1, ev3, ev4)

  @deprecated("binary compatibility only: compiled code from <= 0.2.9 calls this; use the overload without Env", "0.2.10")
  def apply[F[_]](serviceName: String,
                  env: DwollaEnvironment,
                  ev1: Async[F],
                  @unused ev2: Env[F],
                  ev3: LoggerFactory[F],
                  ev4: Random[F],
                 ): Resource[F, EntryPoint[F]] =
    entryPoint(serviceName, None, env, logTraces = false)(ev1, ev3, ev4)

  @deprecated("binary compatibility only: compiled code from <= 0.2.9 calls this; use the overload without Env", "0.2.10")
  def apply[F[_]](serviceName: String,
                  env: DwollaEnvironment,
                  logTraces: Boolean,
                  dispatcher: Dispatcher[F],
                  ev1: Async[F],
                  @unused ev2: Env[F],
                  ev3: LoggerFactory[F],
                  ev4: Random[F],
                 ): Resource[F, EntryPoint[F]] =
    entryPoint(serviceName, None, env, logTraces, dispatcher)(ev1, ev3, ev4)

  @deprecated("binary compatibility only: compiled code from <= 0.2.9 calls this; use the overload without Env", "0.2.10")
  def apply[F[_]](serviceName: String,
                  env: DwollaEnvironment,
                  logTraces: Boolean,
                  ev1: Async[F],
                  @unused ev2: Env[F],
                  ev3: LoggerFactory[F],
                  ev4: Random[F],
                 ): Resource[F, EntryPoint[F]] =
    entryPoint(serviceName, None, env, logTraces)(ev1, ev3, ev4)

}
