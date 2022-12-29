// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.1"

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
    )
  )
