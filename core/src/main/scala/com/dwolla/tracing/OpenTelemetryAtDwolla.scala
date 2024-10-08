package com.dwolla.tracing

import cats.effect.std.*
import cats.effect.syntax.all.*
import cats.effect.{Trace as _, *}
import cats.syntax.all.*
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.{ContextPropagators, TextMapPropagator}
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.extension.trace.propagation.B3Propagator
import io.opentelemetry.sdk.resources.Resource as OTResource
import io.opentelemetry.sdk.trace.`export`.{BatchSpanProcessor, SimpleSpanProcessor}
import io.opentelemetry.sdk.trace.{SdkTracerProvider, SdkTracerProviderBuilder, SpanProcessor}
import natchez.*
import natchez.opentelemetry.OpenTelemetry
import org.typelevel.log4cats.LoggerFactory

object OpenTelemetryAtDwolla {
  def apply[F[_] : Async : Env : LoggerFactory : Random](serviceName: String,
                                                         serviceVersion: String,
                                                         env: DwollaEnvironment): Resource[F, EntryPoint[F]] =
    apply(serviceName, serviceVersion, env, logTraces = false)

  def apply[F[_] : Async : Env : LoggerFactory : Random](serviceName: String,
                                                         serviceVersion: String,
                                                         env: DwollaEnvironment,
                                                         logTraces: Boolean): Resource[F, EntryPoint[F]] =
    Dispatcher.parallel(await = true)
      .flatMap(OpenTelemetryAtDwolla(serviceName, serviceVersion.some, env, logTraces, _))

  def apply[F[_] : Async : Env : LoggerFactory : Random](serviceName: String,
                                                         serviceVersion: Option[String],
                                                         env: DwollaEnvironment,
                                                         logTraces: Boolean,
                                                         dispatcher: Dispatcher[F]): Resource[F, EntryPoint[F]] =
    logTraces
      .guard[Option]
      .traverse { _ =>
        LoggerFactory[F]
          .create
          .toResource
          .map { implicit logger =>
            SimpleSpanProcessor.create(new LoggingSpanExporter(dispatcher))
          }
      }
      .flatMap(buildOtel(serviceName, env, _, AwsXrayIdGenerator(dispatcher).some, serviceVersion))

  private def buildOtel[F[_] : Sync : Env](serviceName: String,
                                           env: DwollaEnvironment,
                                           loggingProcessor: Option[SpanProcessor],
                                           awsXrayIdGenerator: Option[AwsXrayIdGenerator[F]],
                                           version: Option[String],
                                          ): Resource[F, EntryPoint[F]] =
    OpenTelemetry.entryPoint(globallyRegister = true) { sdkBuilder =>
      // TODO consider whether to use the OpenTelemetry SDK Autoconfigure module to support all the environment variables https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure
      Env[F].get("OTEL_EXPORTER_OTLP_ENDPOINT")
        .toResource
        .flatMap { endpoint =>
          Resource.fromAutoCloseable(Sync[F].delay {
              val builder =
                endpoint.foldl(OtlpGrpcSpanExporter.builder())(_ setEndpoint _).build()

              BatchSpanProcessor.builder(builder).build()
            })
            .map(_.pure[List])
            .map(loggingProcessor.toList ::: _)
        }
        .evalMap { spanProcessors =>
          Sync[F].delay {
            sdkBuilder
              .setPropagators {
                ContextPropagators.create {
                  TextMapPropagator.composite(
                    W3CTraceContextPropagator.getInstance(),
                    B3Propagator.injectingMultiHeaders(),
                    AwsXrayPropagator.getInstance(),
                  )
                }
              }
              .setTracerProvider {
                spanProcessors
                  .foldl[SdkTracerProviderBuilder] {
                    awsXrayIdGenerator.foldl {
                      SdkTracerProvider.builder()
                        .setResource {
                          OTResource
                            .getDefault
                            .merge(OTResource.create {
                              version.foldl {
                                Attributes.builder()
                                  .put(OtelAttributes.serviceName, serviceName)
                                  .put(OtelAttributes.deploymentEnvironmentName, env.name)
                              }(_.put(OtelAttributes.serviceVersion, _))
                                .build()
                            })
                        }
                    }(_ setIdGenerator _)
                  }(_ addSpanProcessor _)
                  .build()
              }
          }
        }
    }

  @deprecated("Doesn't log. Unless F[_]: Async, doesn't generate X-Ray compatible trace IDs. Only maintained for binary compatibility.", "0.2.3")
  def apply[F[_]](serviceName: String, env: DwollaEnvironment, F: Sync[F], E: Env[F]): Resource[F, EntryPoint[F]] =
    F match {
      case async: Async[F] =>
        import org.typelevel.log4cats.noop.NoOpFactory

        Random.scalaUtilRandom(F)
          .toResource
          .flatMap {
            OpenTelemetryAtDwolla(serviceName, env)(async, E, NoOpFactory(F), _)
          }
      case _ =>
        buildOtel(serviceName, env, None, None, None)(F, E)
    }

  @deprecated("provide service version; only maintained for binary compatibility", "0.2.3")
  def apply[F[_] : Async : Env : LoggerFactory : Random](serviceName: String,
                                                         env: DwollaEnvironment): Resource[F, EntryPoint[F]] =
    apply(serviceName, env, logTraces = false)
  @deprecated("provide service version; only maintained for binary compatibility", "0.2.3")
  def apply[F[_] : Async : Env : LoggerFactory : Random](serviceName: String,
                                                         env: DwollaEnvironment,
                                                         logTraces: Boolean,
                                                         dispatcher: Dispatcher[F]): Resource[F, EntryPoint[F]] =
    OpenTelemetryAtDwolla(serviceName, None, env, logTraces, dispatcher)

  @deprecated("provide service version; only maintained for binary compatibility", "0.2.3")
  def apply[F[_] : Async : Env : LoggerFactory : Random](serviceName: String,
                                                         env: DwollaEnvironment,
                                                         logTraces: Boolean): Resource[F, EntryPoint[F]] =
    Dispatcher.parallel(await = true)
      .flatMap(OpenTelemetryAtDwolla(serviceName, None, env, logTraces, _))

}
