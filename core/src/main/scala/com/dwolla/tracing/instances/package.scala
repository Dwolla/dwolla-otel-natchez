package com.dwolla.tracing

import cats._
import cats.data._
import cats.effect.{Trace => _, _}
import cats.mtl._
import natchez._

package object instances extends LocalInstances

package instances {
  trait LocalInstances {
    /** When fulfilling a effect-polymorphic function demanding an implicit
     * `Local[F, Span[F]]` with `[F[_], A] =>> Kleisli[F, Span[F], A]`,
     * the implicit instances provided by cats-mtl don't quite fit. The
     * code demands
     * `Local[Kleisli[F, Span[F], *], Span[Kleisli[F, Span[F], *]]`,
     * but `Local.baseLocalForKleisli` returns
     * `Local[Kleisli[F, Span[F], *], Span[F]]` (the `E` parameter is
     * wrong), and `Local.localForKleisli` requires an implicit
     * `Local[F, Span[Kleisli[F, Span[F], *]]`, which we don't have.
     *
     * However, it's safe to `imap` the `Local[Kleisli[F, Span[F], *], Span[F]]`
     * returned by `Local.baseLocalForKleisli` using `Kleisli.liftK` and
     * `Kleisli.applyK`. `Kleisli.liftK` will turn `Span[F]` into
     * `Span[Kleisli[F, Span[F], *]]`, which, when run, will ignore any
     * `Span[F]`s passed to each `Kleisli[F, Span[F], *]`. We take
     * advantage of that by immediately `mapK`ing it back to `Span[F]`,
     * applying a no-op span that we know will never actually be used.
     *
     * This instance is law-tested in [[https://github.com/tpolecat/natchez/pull/713 tpolecat/natchez#713]],
     * so it's believed to be a lawful instance.
     *
     * TODO Remove when [[https://github.com/tpolecat/natchez/pull/713 tpolecat/natchez#713]] is merged and released
     *
     * @param F the `MonadCancel[F, _]` instance demanded by `Span[F].mapK`
     * @tparam F the effect in which to operate
     * @return a `Local[Kleisli[F, Span[F], *], Span[Kleisli[F, Span[F], *]]]` instance
     */
    implicit def localSpanViaKleisli[F[_]](implicit F: MonadCancel[F, _]): Local[Kleisli[F, Span[F], *], Span[Kleisli[F, Span[F], *]]] =
      new Local[Kleisli[F, Span[F], *], Span[Kleisli[F, Span[F], *]]] {
        override def local[A](fa: Kleisli[F, Span[F], A])
                             (f: Span[Kleisli[F, Span[F], *]] => Span[Kleisli[F, Span[F], *]]): Kleisli[F, Span[F], A] =
          fa.local {
            f.andThen(_.mapK(Kleisli.applyK(Span.noop[F])))
              .compose(_.mapK(Kleisli.liftK))
          }

        override def applicative: Applicative[Kleisli[F, Span[F], *]] =
          Kleisli.catsDataApplicativeForKleisli

        override def ask[E2 >: Span[Kleisli[F, Span[F], *]]]: Kleisli[F, Span[F], E2] =
          Kleisli.ask[F, Span[F]].map(_.mapK(Kleisli.liftK))
      }
  }
}
