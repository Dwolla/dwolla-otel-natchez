// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.2"

ThisBuild / organization := "com.dwolla"
ThisBuild / organizationName := "Dwolla"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  tlGitHubDev("bpholt", "Brian Holt")
)

ThisBuild / tlSonatypeUseLegacyHost := true

val Scala3 = "3.3.1"
ThisBuild / crossScalaVersions := Seq(Scala3, "2.13.12", "2.12.18")
ThisBuild / scalaVersion := Scala3 // the default Scala
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowScalaVersions := Seq("3", "2.13", "2.12")
ThisBuild / tlJdkRelease := Some(8)
ThisBuild / tlCiReleaseBranches := Seq("main")
ThisBuild / tlVersionIntroduced := Map("3" -> "0.2.2")
ThisBuild / mergifyStewardConfig ~= { _.map(_.copy(
  author = "dwolla-oss-scala-steward[bot]",
  mergeMinors = true,
))}

lazy val root = tlCrossRootProject.aggregate(core)

lazy val core = project.in(file("core"))
  .settings(
    name := "dwolla-otel-natchez",
    description := "Utilities for configuring a Natchez EntryPoint for OpenTelemetry at Dwolla",
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % "0.3.5",
      "org.tpolecat" %% "natchez-opentelemetry" % "0.3.5",
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.2",
      "org.typelevel" %% "cats-effect-kernel" % "3.5.2",
      "org.typelevel" %% "cats-effect-std" % "3.5.2",
      "org.typelevel" %% "cats-mtl" % "1.4.0",
      "io.opentelemetry" % "opentelemetry-api" % "1.33.0",
      "io.opentelemetry" % "opentelemetry-context" % "1.33.0",
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.33.0",
      "io.opentelemetry" % "opentelemetry-extension-trace-propagators" % "1.33.0",
      "io.opentelemetry" % "opentelemetry-sdk" % "1.33.0",
      "io.opentelemetry" % "opentelemetry-sdk-common" % "1.33.0",
      "io.opentelemetry" % "opentelemetry-sdk-trace" % "1.33.0",
      "io.opentelemetry.semconv" % "opentelemetry-semconv" % "1.23.1-alpha",
      "io.opentelemetry.contrib" % "opentelemetry-aws-resources" % "1.32.0-alpha",
      "io.opentelemetry.contrib" % "opentelemetry-aws-xray-propagator" % "1.32.0-alpha",
      "io.opentelemetry.contrib" % "opentelemetry-aws-xray" % "1.32.0",
    )
  )
