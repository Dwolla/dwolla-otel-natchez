package com.dwolla.tracing

import cats.effect.std.*
import cats.effect.syntax.all.*
import cats.effect.{Trace as _, *}
import cats.syntax.all.*
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.{ContextPropagators, TextMapPropagator}
import io.opentelemetry.contrib.aws.resource.{Ec2Resource, EcsResource}
import io.opentelemetry.contrib.awsxray.AwsXrayIdGenerator
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.extension.trace.propagation.B3Propagator
import io.opentelemetry.sdk.resources.Resource as OTResource
import io.opentelemetry.sdk.trace.{SdkTracerProvider, SdkTracerProviderBuilder, SpanProcessor}
import io.opentelemetry.sdk.trace.`export`.{BatchSpanProcessor, SimpleSpanProcessor}
import io.opentelemetry.semconv.ResourceAttributes
import natchez.*
import natchez.opentelemetry.OpenTelemetry
import org.typelevel.log4cats.LoggerFactory

object OpenTelemetryAtDwolla {
  def apply[F[_] : Sync : Env](serviceName: String,
                               env: DwollaEnvironment): Resource[F, EntryPoint[F]] =
    buildOtel(serviceName, env, None)

  def apply[F[_] : Async : Env : LoggerFactory](serviceName: String,
                                                env: DwollaEnvironment,
                                                logTraces: Boolean): Resource[F, EntryPoint[F]] =
    logTraces
      .guard[Option]
      .traverse { _ =>
        LoggerFactory[F]
          .create
          .toResource
          .flatMap { implicit logger =>
            Dispatcher.sequential(true)
              .map(new LoggingSpanExporter(_))
              .map(SimpleSpanProcessor.create)
          }
      }
      .flatMap(buildOtel(serviceName, env, _))

  private def buildOtel[F[_] : Sync : Env](serviceName: String,
                                           env: DwollaEnvironment,
                                           loggingProcessor: Option[SpanProcessor],
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
                    SdkTracerProvider.builder()
                      .setResource {
                        OTResource
                          .getDefault
                          .merge(OTResource.create(Attributes.of(
                            ResourceAttributes.SERVICE_NAME, serviceName,
                            ResourceAttributes.DEPLOYMENT_ENVIRONMENT, env.name,
                          )))
                          .merge(Ec2Resource.get())
                          .merge(EcsResource.get())
                      }
                      .setIdGenerator(AwsXrayIdGenerator.getInstance())
                  }(_ addSpanProcessor _)
                  .build()
              }
          }
        }
    }
}
