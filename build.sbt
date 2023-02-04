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

val Scala213 = "2.13.10"
ThisBuild / crossScalaVersions := Seq(Scala213, "2.12.17")
ThisBuild / scalaVersion := Scala213 // the default Scala
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowScalaVersions := Seq("2.13", "2.12")
ThisBuild / tlJdkRelease := Some(8)
ThisBuild / tlCiReleaseBranches := Seq("main")

lazy val root = tlCrossRootProject.aggregate(core)

lazy val core = project.in(file("core"))
  .settings(
    name := "dwolla-otel-natchez",
    description := "Utilities for configuring a Natchez EntryPoint for OpenTelemetry at Dwolla",
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % "0.3.0",
      "org.tpolecat" %% "natchez-opentelemetry" % "0.3.0",
      "org.typelevel" %% "cats-core" % "2.9.0",
      "org.typelevel" %% "cats-effect" % "3.4.6",
      "org.typelevel" %% "cats-effect-kernel" % "3.4.6",
      "org.typelevel" %% "cats-effect-std" % "3.4.6",
      "org.typelevel" %% "cats-mtl" % "1.3.0",
      "io.opentelemetry" % "opentelemetry-api" % "1.22.0",
      "io.opentelemetry" % "opentelemetry-context" % "1.22.0",
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.22.0",
      "io.opentelemetry" % "opentelemetry-extension-trace-propagators" % "1.22.0",
      "io.opentelemetry" % "opentelemetry-sdk" % "1.22.0",
      "io.opentelemetry" % "opentelemetry-sdk-common" % "1.22.0",
      "io.opentelemetry" % "opentelemetry-sdk-trace" % "1.22.0",
      "io.opentelemetry" % "opentelemetry-semconv" % "1.21.0-alpha",
      "io.opentelemetry.contrib" % "opentelemetry-aws-resources" % "1.22.0-alpha",
      "io.opentelemetry.contrib" % "opentelemetry-aws-xray-propagator" % "1.22.0-alpha",
      "io.opentelemetry.contrib" % "opentelemetry-aws-xray" % "1.22.0",
    )
  )
