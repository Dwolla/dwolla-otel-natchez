/** Decides whether a module's resolved classpath mixes OpenTelemetry release lines. Pure, so the meta-build tests it. */
object OtelVersionCheck {

  final case class ResolvedModule(organization: String, name: String, revision: String, callers: List[String]) {
    def coordinates: String = s"$organization:$name:$revision"

    def pulledInBy: String =
      if (callers.isEmpty) "no caller information available"
      else s"pulled in by: ${callers.mkString(", ")}"
  }

  sealed trait Problem {
    def describe: String
  }

  final case class WrongVersion(module: ResolvedModule, expectedBaseVersion: String) extends Problem {
    override def describe: String =
      s"${module.coordinates} is not on the pinned ${module.organization} line $expectedBaseVersion; ${module.pulledInBy}"
  }

  final case class UnpinnedOrganization(module: ResolvedModule) extends Problem {
    override def describe: String =
      s"${module.coordinates} is in ${module.organization}, which has no pinned version in build.sbt; ${module.pulledInBy}"
  }

  // semconv is versioned on its own line and is only a Test dependency in this build
  private val ignoredOrganizations = Set("io.opentelemetry.semconv")

  def problems(expectedBaseVersions: Map[String, String], resolved: List[ResolvedModule]): List[Problem] =
    resolved
      .filter(m => isOpenTelemetry(m.organization) && !ignoredOrganizations.contains(m.organization))
      .flatMap { m =>
        expectedBaseVersions.get(m.organization) match {
          case None => List(UnpinnedOrganization(m))
          case Some(expected) if baseVersion(m.revision) == expected => Nil
          case Some(expected) => List(WrongVersion(m, expected))
        }
      }

  /** Inverts dependency-graph edges, `(dependent, dependency)` in `organization:name:revision` form, into each module's dependents. */
  def callersByModule(edges: List[(String, String)]): Map[String, List[String]] =
    edges.groupBy(_._2).map { case (dependency, es) => dependency -> es.map(_._1).distinct.sorted }

  private def isOpenTelemetry(organization: String): Boolean =
    organization == "io.opentelemetry" || organization.startsWith("io.opentelemetry.")

  private def baseVersion(revision: String): String =
    revision.stripSuffix("-alpha")
}
