package com.dwolla.tracing

import cats.effect.*
import cats.mtl.*

package object instances extends LocalInstances

package instances {
  trait LocalInstances {
    @deprecated("use IO.local instead", "0.2.6")
    def catsMtlEffectLocalForIO[E](implicit ioLocal: IOLocal[E]): Local[IO, E] =
      ioLocal.asLocal
  }
}
