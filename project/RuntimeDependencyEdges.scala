package sbt

/**
 * The runtime dependency graph's edges that `dependencyTree` prints, as `(dependent, dependency)` pairs of
 * `organization:name:revision`. Lives in `package sbt` because sbt keeps the graph's key and type
 * `private[sbt]`, and `update`'s own `ModuleReport.callers` is empty with Coursier.
 *
 * Reaching a `private[sbt]` key through `package sbt` depends on sbt internals, so an sbt upgrade (certainly
 * sbt 2) may require updating this file; the failure is a loud meta-build compile error.
 */
object RuntimeDependencyEdges {
  val value: Def.Initialize[Task[List[(String, String)]]] = Def.task {
    (Runtime / plugins.DependencyTreeKeys.dependencyTreeModuleGraph0).value.edges.toList.map { case (from, to) =>
      (s"${from.organization}:${from.name}:${from.version}", s"${to.organization}:${to.name}:${to.version}")
    }
  }
}
