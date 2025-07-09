package com.dwolla.tracing.scalafix

import scalafix.v1.*

import scala.meta.*

object DwollaOtelNatchezCatsEffect36Migration extends SyntacticRule("DwollaOtelNatchezCatsEffect36Migration") {
  override def description: String = "Removes deprecated implicits and rewrites `IOLocal` to `IO.local`"

  override def isRewrite: Boolean = true

  override def fix(implicit doc: SyntacticDocument): Patch =
    doc.tree.collect {
      // Remove import `com.dwolla.tracing.instances.catsMtlEffectLocalForIO`, `…._`, or `….*`
      case Importer(
        Term.Select(
          Term.Select(
            Term.Select(
              Term.Name("com"),
              Term.Name("dwolla")
            ),
            Term.Name("tracing")
          ),
          Term.Name("instances")
        ),
        List(i)
      ) =>
        Patch.removeImportee(i)

      case t @ // change `IOLocal(Span.noop[IO]).map(catsMtlEffectLocalForIO(_))` to `IO.local(Span.noop[IO])`
        Term.Apply.After_4_6_0(
          IOLocalOfNoopSpan(),
          Term.ArgClause((List(AnonymousFunctionCallingCatsMtlEffectLocalForIO() | OnymousFunctionCallingCatsMtlEffectLocalForIO()), _))
        ) =>
        Patch.replaceTree(t, s"IO.local(Span.noop[IO])")

    }.asPatch
}

trait BooleanUnapplyFromPartialFunction[T] {
  protected val pf: PartialFunction[T, Unit]

  def unapply(t: T): Boolean = pf.isDefinedAt(t)
}

object OnymousFunctionCallingCatsMtlEffectLocalForIO extends BooleanUnapplyFromPartialFunction[Term] {
  override protected val pf: PartialFunction[Term, Unit] = {
    case Term.Function.After_4_6_0(
      Term.ParamClause((List(Term.Param(_, Term.Name(name), _, _)), _)),
      Term.Apply.After_4_6_0(
        Term.Name("catsMtlEffectLocalForIO"),
        Term.ArgClause((List(Term.Name(nameApplied)), _))
      )
    ) if name == nameApplied => ()
  }
}

object AnonymousFunctionCallingCatsMtlEffectLocalForIO extends BooleanUnapplyFromPartialFunction[Term] {
  override protected val pf: PartialFunction[Term, Unit] = {
    case Term.AnonymousFunction(Term.Apply.After_4_6_0(Term.Name("catsMtlEffectLocalForIO"), _)) => ()
  }
}

object IOLocalOfNoopSpan extends BooleanUnapplyFromPartialFunction[Term] {
  override protected val pf: PartialFunction[Term, Unit] = {
    case Term.Select(
           Term.Apply.After_4_6_0(Term.Name("IOLocal"),
             Term.ArgClause(List(
               Term.ApplyType.After_4_6_0(
                 Term.Select(Term.Name("Span"), Term.Name("noop")),
                 Type.ArgClause(List(Type.Name("IO")))
               )
             ), _)
           ),
           Term.Name("map")
         ) => ()
  }
}
