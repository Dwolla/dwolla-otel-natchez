/*
 * Copyright 2024 Dwolla, Inc
 * SPDX-License-Identifier: Apache-2.0
 * Based on https://github.com/open-telemetry/opentelemetry-java-contrib/blob/eece7e8ef04170fb463ddf692f61d4527b50febf/aws-xray/src/main/java/io/opentelemetry/contrib/awsxray/AwsXrayIdGenerator.java
 */
package com.dwolla.tracing

import cats.effect.*
import cats.effect.std.*
import cats.*
import cats.syntax.all.*
import io.opentelemetry.api.trace.{SpanId, TraceId}
import io.opentelemetry.sdk.trace.IdGenerator

object AwsXrayIdGenerator {
  def apply[F[_] : Applicative : Clock : Random](dispatcher: Dispatcher[F]): AwsXrayIdGenerator[F] =
    new AwsXrayIdGenerator(dispatcher)
}

class AwsXrayIdGenerator[F[_] : Applicative : Clock : Random](dispatcher: Dispatcher[F]) extends IdGenerator {
  override def generateSpanId(): String =
    dispatcher.unsafeRunSync {
      Random[F]
        .nextLong
        .map(SpanId.fromLong)
    }

  override def generateTraceId(): String = dispatcher.unsafeRunSync {
    (Clock[F].realTime.map(_.toSeconds),
      Random[F].nextInt.map(_ & 0xFFFFFFFFL),
      Random[F].nextLong
    ).mapN { case (timestampSecs, hiRandom, lowRandom) =>
      TraceId.fromLongs(timestampSecs << 32 | hiRandom, lowRandom)
    }
  }
}
