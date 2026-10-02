import com.typesafe.tools.mima.core._

ThisBuild / tlBaseVersion := "0.2"

ThisBuild / organization := "com.dwolla"
ThisBuild / organizationName := "Dwolla"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  tlGitHubDev("bpholt", "Brian Holt")
)

val Scala3 = "3.3.8"
ThisBuild / crossScalaVersions := Seq(Scala3, "2.13.18", "2.12.21")
ThisBuild / scalaVersion := Scala3 // the default Scala
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowScalaVersions := Seq("3", "2.13", "2.12")
ThisBuild / tlJdkRelease := Some(8)
ThisBuild / tlCiReleaseBranches := Seq("main")
ThisBuild / tlVersionIntroduced := Map("3" -> "0.2.2")
ThisBuild / mergifyStewardConfig ~= { _.map {
  _.withAuthor("dwolla-oss-scala-steward[bot]")
    .withMergeMinors(true)
}}

lazy val root = tlCrossRootProject.aggregate(
  core,
  loggingSpanExporter,
  natchez,
  `aws-xray-id-generator`,
  `dwolla-xray-annotations`,
  testkit,
)

lazy val catsEffectV = "3.7.1"

// Every stable io.opentelemetry artifact is declared at otelV; checkOtelVersions enforces that the
// resolved classpath agrees, because mixed OTel Java versions fail at runtime (NoClassDefFoundError).
lazy val otelV = "1.66.0"
// io.opentelemetry.instrumentation, used as both "2.31.1" and "2.31.1-alpha". 2.31.1 is built against
// OTel 1.65.0; no release built against otelV exists yet.
lazy val otelInstrumentationV = "2.31.1"
// io.opentelemetry.contrib, used as "1.52.0-alpha" (built against OTel 1.56.0).
lazy val otelContribV = "1.52.0"
lazy val otel4sV = "1.1.0"

lazy val otelBaseVersions: Map[String, String] = Map(
  "io.opentelemetry" -> otelV,
  "io.opentelemetry.instrumentation" -> otelInstrumentationV,
  "io.opentelemetry.contrib" -> otelContribV,
)

lazy val checkOtelVersions = taskKey[Unit]("Fails if this module's resolved runtime classpath mixes OpenTelemetry release lines")

lazy val otelVersionCheckSettings: Seq[Def.Setting[_]] = Seq(
  checkOtelVersions := {
    val log = streams.value.log
    val runtimeReport = update.value.configuration(Runtime.toConfigRef).toList
    // update's ModuleReport.callers is empty with Coursier; see RuntimeDependencyEdges
    val callers = OtelVersionCheck.callersByModule(
      RuntimeDependencyEdges.value.value
    )
    if (callers.isEmpty && runtimeReport.exists(_.modules.nonEmpty))
      sys.error(s"${name.value}: the dependency graph has no edges, so problems could not name what pulled them in")
    val resolved =
      runtimeReport
        .flatMap(_.modules)
        .filterNot(_.evicted)
        .map { report =>
          val module = report.module
          OtelVersionCheck.ResolvedModule(
            module.organization,
            module.name,
            module.revision,
            callers.getOrElse(s"${module.organization}:${module.name}:${module.revision}", Nil),
          )
        }
    OtelVersionCheck.problems(otelBaseVersions, resolved) match {
      case Nil =>
        log.info(s"${name.value}: OpenTelemetry versions are consistent")
      case problems =>
        problems.foreach(p => log.error(p.describe))
        sys.error(s"${name.value}: ${problems.size} OpenTelemetry version problem(s); see the errors above")
    }
  },
)

lazy val core = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "dwolla-otel-core",
    description := "Dwolla environment and OpenTelemetry resource attribute names, with no OpenTelemetry dependency",
    libraryDependencies ++= Seq(
      // the semconv artifacts' AttributeKey constants need opentelemetry-api on the test classpath, and
      // semconv's POM declares no dependencies
      "io.opentelemetry" % "opentelemetry-api" % otelV % Test,
      "io.opentelemetry.semconv" % "opentelemetry-semconv" % "1.44.0" % Test,
      "io.opentelemetry.semconv" % "opentelemetry-semconv-incubating" % "1.37.0-alpha" % Test,
      "org.scalameta" %%% "munit" % "1.3.6" % Test,
    ),
  )
  .jvmSettings(
    tlVersionIntroduced := List("2.12", "2.13", "3").map(_ -> "0.2.9").toMap,
  )

lazy val loggingSpanExporter = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("logging-span-exporter"))
  .settings(
    name := "otel-logging-span-exporter",
    otelVersionCheckSettings,
    description := "An OpenTelemetry Java SpanExporter that logs spans as OTLP-shaped JSON through log4cats",
    // scala.jdk.CollectionConverters only exists on 2.13+; the compat library provides it on 2.12
    libraryDependencies ++= (
      if (scalaBinaryVersion.value == "2.12") Seq("org.scala-lang.modules" %%% "scala-collection-compat" % "2.14.0")
      else Seq.empty
    ),
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "2.13.0",
      "org.typelevel" %%% "cats-effect" % catsEffectV,
      "org.typelevel" %%% "log4cats-core" % "2.8.0",
      "io.circe" %%% "circe-literal" % "0.14.16",
      "org.typelevel" %%% "jawn-parser" % "1.8.0" % Provided,
      "io.opentelemetry" % "opentelemetry-api" % otelV,
      "io.opentelemetry" % "opentelemetry-sdk-common" % otelV,
      "io.opentelemetry" % "opentelemetry-sdk-trace" % otelV,
      // LoggingSpanExporter uses the OTLP proto enum classes shipped (as internal API) in the OTLP exporter
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % otelV,
    ),
  )
  .jvmSettings(
    tlVersionIntroduced := List("2.12", "2.13", "3").map(_ -> "0.2.9").toMap,
  )

lazy val natchez = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("natchez"))
  .settings(
    name := "dwolla-otel-natchez",
    otelVersionCheckSettings,
    description := "Utilities for configuring a Natchez EntryPoint for OpenTelemetry at Dwolla",
    // DwollaEnvironment and LoggingSpanExporter live in dwolla-otel-core and otel-logging-span-exporter,
    // with the same fully-qualified names, so consumers still get them through those dependencies;
    // MiMa compares this jar alone.
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[MissingClassProblem]("com.dwolla.tracing.DwollaEnvironment*"),
      ProblemFilters.exclude[MissingClassProblem]("com.dwolla.tracing.LoggingSpanExporter*"),
    ),
    libraryDependencies ++= {
      Seq(
        "org.tpolecat" %%% "natchez-core" % "0.3.10",
        "org.tpolecat" %%% "natchez-opentelemetry" % "0.3.10",
        "org.typelevel" %%% "cats-core" % "2.13.0",
        "org.typelevel" %%% "cats-effect" % catsEffectV,
        "org.typelevel" %%% "cats-mtl" % "1.7.0",
        "org.typelevel" %%% "log4cats-core" % "2.8.0",
        "io.circe" %%% "circe-literal" % "0.14.16",
        "org.typelevel" %%% "jawn-parser" % "1.8.0" % Provided,
        "io.opentelemetry" % "opentelemetry-api" % otelV,
        "io.opentelemetry" % "opentelemetry-context" % otelV,
        "io.opentelemetry" % "opentelemetry-exporter-otlp" % otelV,
        "io.opentelemetry" % "opentelemetry-extension-trace-propagators" % otelV,
        "io.opentelemetry" % "opentelemetry-sdk" % otelV,
        "io.opentelemetry" % "opentelemetry-sdk-common" % otelV,
        "io.opentelemetry" % "opentelemetry-sdk-trace" % otelV,
        "io.opentelemetry.contrib" % "opentelemetry-aws-xray-propagator" % s"$otelContribV-alpha",
        "io.opentelemetry.semconv" % "opentelemetry-semconv" % "1.44.0" % Test,
        "io.opentelemetry.semconv" % "opentelemetry-semconv-incubating" % "1.37.0-alpha" % Test,
        "org.scalameta" %%% "munit" % "1.3.6" % Test,
      )
    },
  )
  .dependsOn(core, loggingSpanExporter, `aws-xray-id-generator`)

lazy val `aws-xray-id-generator` = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("aws-xray-id-generator"))
  .settings(
    name := "otel-aws-xray-id-generator",
    otelVersionCheckSettings,
    description := "Generate OTel trace IDs compatible with AWS X-Ray with minimal dependencies",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffectV,
      "io.opentelemetry" % "opentelemetry-api" % otelV,
      "io.opentelemetry" % "opentelemetry-sdk-trace" % otelV,
    ),
  )
  .jvmSettings(
    tlVersionIntroduced := Map("2.12" -> "0.2.3", "2.13" -> "0.2.3", "3" -> "0.2.3"),
  )

lazy val `dwolla-xray-annotations` = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("dwolla-xray-annotations"))
  .settings(
    name := "dwolla-xray-annotations",
    description := "Constants for OTel attribute names that should be indexed by X-Ray as annotations",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.3.6" % Test,
    ),
  )
  .jvmSettings(
    tlVersionIntroduced := List("2.12", "2.13", "3").map(_ -> "0.2.9").toMap,
  )

lazy val testkit = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("testkit"))
  .settings(
    name := "dwolla-otel-natchez-testkit",
    libraryDependencies ++= {
      Seq(
        "org.tpolecat" %%% "natchez-core" % "0.3.10",
        "org.tpolecat" %%% "natchez-testkit" % "0.3.10",
        "org.typelevel" %%% "munit-cats-effect" % "2.2.1",
      )
    },
  )
  .jvmSettings(
    tlVersionIntroduced := Map("2.12" -> "0.2.8", "2.13" -> "0.2.8", "3" -> "0.2.8"),
  )
  .dependsOn(natchez)

ThisBuild / githubWorkflowBuild ++= Seq(
  WorkflowStep.Sbt(
    List("checkOtelVersions"),
    name = Some("Check OpenTelemetry versions"),
  ),
  WorkflowStep.Sbt(
    List("reload plugins", "test"),
    name = Some("Test the build's own code"),
    cond = Some("matrix.scala == '3'"),
  ),
)
