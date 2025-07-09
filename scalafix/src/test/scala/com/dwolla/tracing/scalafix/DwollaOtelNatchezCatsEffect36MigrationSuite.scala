package com.dwolla.tracing.scalafix

import org.scalatest.funsuite.AnyFunSuiteLike
import scalafix.testkit.*

class DwollaOtelNatchezCatsEffect36MigrationSuite extends AbstractSyntacticRuleSuite with AnyFunSuiteLike {
  private val output =
    """package fix
      |
      |import cats.effect.*
      |import natchez.Span
      |
      |object DwollaOtelNatchezCatsEffect36MigrationWithExplicitImport extends IOApp {
      |  override def run(args: List[String]): IO[ExitCode] =
      |    IO.local(Span.noop[IO])
      |      .as(ExitCode.Success)
      |}
      |""".stripMargin

  check(
    DwollaOtelNatchezCatsEffect36Migration,
    "DwollaOtelNatchezCatsEffect36Migration with explicit import",
    """package fix
      |
      |import cats.effect.*
      |import com.dwolla.tracing.instances.catsMtlEffectLocalForIO
      |import natchez.Span
      |
      |object DwollaOtelNatchezCatsEffect36MigrationWithExplicitImport extends IOApp {
      |  override def run(args: List[String]): IO[ExitCode] =
      |    IOLocal(Span.noop[IO])
      |      .map(catsMtlEffectLocalForIO(_))
      |      .as(ExitCode.Success)
      |}
      |""".stripMargin,
    output,
  )

  check(
    DwollaOtelNatchezCatsEffect36Migration,
    "DwollaOtelNatchezCatsEffect36Migration with Scala 2 wildcard import",
    """package fix
      |
      |import cats.effect.*
      |import com.dwolla.tracing.instances._
      |import natchez.Span
      |
      |object DwollaOtelNatchezCatsEffect36MigrationWithExplicitImport extends IOApp {
      |  override def run(args: List[String]): IO[ExitCode] =
      |    IOLocal(Span.noop[IO])
      |      .map(a => catsMtlEffectLocalForIO(a))
      |      .as(ExitCode.Success)
      |}
      |""".stripMargin,
    output,
  )

  check(
    DwollaOtelNatchezCatsEffect36Migration,
    "DwollaOtelNatchezCatsEffect36Migration with Scala 3 wildcard import",
    """package fix
      |
      |import cats.effect.*
      |import com.dwolla.tracing.instances.*
      |import natchez.Span
      |
      |object DwollaOtelNatchezCatsEffect36MigrationWithExplicitImport extends IOApp {
      |  override def run(args: List[String]): IO[ExitCode] =
      |    IOLocal(Span.noop[IO]).map(catsMtlEffectLocalForIO(_))
      |      .as(ExitCode.Success)
      |}
      |""".stripMargin,
    output,
  )

}
