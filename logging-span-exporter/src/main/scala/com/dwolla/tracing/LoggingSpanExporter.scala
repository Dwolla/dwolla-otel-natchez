package com.dwolla.tracing

import cats.*
import cats.syntax.all.*
import cats.effect.std.*
import com.dwolla.tracing.LoggingSpanExporter.*
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.`export`.SpanExporter
import io.opentelemetry.sdk.trace.data.{EventData, LinkData, SpanData, StatusData}
import org.typelevel.log4cats.Logger
import io.circe.literal.*
import io.circe.syntax.*
import io.circe.*
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.{SpanContext, SpanKind, StatusCode}
import io.opentelemetry.proto.trace.v1.internal.{Span, Status}

import java.util
import scala.jdk.CollectionConverters.*

private[tracing] class LoggingSpanExporter[F[_] : Applicative : Logger](dispatcher: Dispatcher[F]) extends SpanExporter {
  override def `export`(spans: util.Collection[SpanData]): CompletableResultCode =
    dispatcher.unsafeRunSync {
      Logger[F].info {
        spans
          .asScala
          .toList
          .map(_.asJson)
          .map(_.noSpaces)
          .mkString("\n")
      }
        .as(CompletableResultCode.ofSuccess())
    }

  override def flush(): CompletableResultCode =
    CompletableResultCode.ofSuccess()

  override def shutdown(): CompletableResultCode =
    CompletableResultCode.ofSuccess()
}

object LoggingSpanExporter {
  implicit val AttributesEncoder: Encoder[Attributes] =
    Encoder[Map[String, String]].contramap {
      _.asMap().asScala.toMap.map { case (k, v) =>
        k.getKey -> v.toString
      }
    }

  implicit val SpanKindEncoder: Encoder[SpanKind] =
    Encoder[Int].contramap {
      case SpanKind.INTERNAL => Span.SpanKind.SPAN_KIND_INTERNAL.getEnumNumber
      case SpanKind.SERVER => Span.SpanKind.SPAN_KIND_SERVER.getEnumNumber
      case SpanKind.CLIENT => Span.SpanKind.SPAN_KIND_CLIENT.getEnumNumber
      case SpanKind.PRODUCER => Span.SpanKind.SPAN_KIND_PRODUCER.getEnumNumber
      case SpanKind.CONSUMER => Span.SpanKind.SPAN_KIND_CONSUMER.getEnumNumber
    }

  implicit val EventDataEncoder: Encoder[EventData] = // TODO implement a real encoder
    Encoder.AsObject.instance(_ => JsonObject.empty)

  implicit val SpanContextEncoder: Encoder[SpanContext] = // TODO implement a real encoder
    Encoder.AsObject.instance(_ => JsonObject.empty)

  implicit val LinkDataEncoder: Encoder[LinkData] = Encoder.instance { ld =>
    json"""{
      "spanContext": ${ld.getSpanContext},
      "attributes": ${ld.getAttributes}
    }"""
  }

  implicit val StatusCodeEncoder: Encoder[StatusCode] = Encoder[Int].contramap {
    case StatusCode.OK => Status.StatusCode.STATUS_CODE_OK.getEnumNumber
    case StatusCode.ERROR => Status.StatusCode.STATUS_CODE_ERROR.getEnumNumber
    case StatusCode.UNSET => Status.StatusCode.STATUS_CODE_UNSET.getEnumNumber
  }

  implicit val StatusDataEncoder: Encoder[StatusData] = Encoder.instance { sd =>
    json"""{
      "code": ${sd.getStatusCode},
      "description": ${sd.getDescription}
    }"""
  }

  implicit val SpanDataEncoder: Encoder[SpanData] = Encoder.instance { sp =>
    json"""
      {
        "resource": {
          "attributes": []
        },
        "scopeSpans": [
          {
            "scope": {
              "name": ${sp.getInstrumentationScopeInfo.getName},
              "attributes": ${sp.getInstrumentationScopeInfo.getAttributes}
            },
            "spans": [
              {
                "traceId": ${sp.getTraceId},
                "spanId": ${sp.getSpanId},
                "name": ${sp.getName},
                "kind": ${sp.getKind},
                "startTimeUnixNano": ${sp.getStartEpochNanos},
                "endTimeUnixNano": ${sp.getEndEpochNanos},
                "attributes": ${sp.getAttributes},
                "events": ${sp.getEvents.asScala},
                "links": ${sp.getLinks.asScala},
                "status": ${sp.getStatus}
              }
            ]
          }
        ]
      }"""
  }
}
