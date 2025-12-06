ThisBuild / tlBaseVersion := "0.2"

ThisBuild / organization := "com.dwolla"
ThisBuild / organizationName := "Dwolla"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  tlGitHubDev("bpholt", "Brian Holt")
)

val Scala3 = "3.3.7"
ThisBuild / crossScalaVersions := Seq(Scala3, "2.13.18", "2.12.20")
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
  `aws-xray-id-generator`,
  `dwolla-xray-annotations`,
  testkit,
)

lazy val catsEffectV = "3.6.3"
lazy val otelApiV = "1.57.0"
lazy val otelTraceSdkV = "1.57.0"

lazy val core = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "dwolla-otel-natchez",
    description := "Utilities for configuring a Natchez EntryPoint for OpenTelemetry at Dwolla",
    libraryDependencies ++= {
      Seq(
        "org.tpolecat" %%% "natchez-core" % "0.3.8",
        "org.tpolecat" %%% "natchez-opentelemetry" % "0.3.8",
        "org.typelevel" %%% "cats-core" % "2.13.0",
        "org.typelevel" %%% "cats-effect" % catsEffectV,
        "org.typelevel" %%% "cats-mtl" % "1.6.0",
        "org.typelevel" %%% "log4cats-core" % "2.7.1",
        "io.circe" %%% "circe-literal" % "0.14.15",
        "org.typelevel" %%% "jawn-parser" % "1.6.0" % Provided,
        "io.opentelemetry" % "opentelemetry-api" % otelApiV,
        "io.opentelemetry" % "opentelemetry-context" % "1.57.0",
        "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.57.0",
        "io.opentelemetry" % "opentelemetry-extension-trace-propagators" % "1.57.0",
        "io.opentelemetry" % "opentelemetry-sdk" % "1.57.0",
        "io.opentelemetry" % "opentelemetry-sdk-common" % "1.57.0",
        "io.opentelemetry" % "opentelemetry-sdk-trace" % otelTraceSdkV,
        "io.opentelemetry.contrib" % "opentelemetry-aws-xray-propagator" % "1.52.0-alpha",
        "io.opentelemetry.semconv" % "opentelemetry-semconv" % "1.37.0" % Test,
        "io.opentelemetry.semconv" % "opentelemetry-semconv-incubating" % "1.37.0-alpha" % Test,
        "org.scalameta" %%% "munit" % "1.2.1" % Test,
      )
    },
  )
  .dependsOn(`aws-xray-id-generator`)

lazy val `aws-xray-id-generator` = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("aws-xray-id-generator"))
  .settings(
    name := "otel-aws-xray-id-generator",
    description := "Generate OTel trace IDs compatible with AWS X-Ray with minimal dependencies",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffectV,
      "io.opentelemetry" % "opentelemetry-api" % otelApiV,
      "io.opentelemetry" % "opentelemetry-sdk-trace" % otelTraceSdkV,
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
      "org.scalameta" %%% "munit" % "1.2.1" % Test,
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
        "org.tpolecat" %%% "natchez-core" % "0.3.8",
        "org.tpolecat" %%% "natchez-testkit" % "0.3.8",
        "org.typelevel" %%% "munit-cats-effect" % "2.1.0",
      )
    },
  )
  .jvmSettings(
    tlVersionIntroduced := Map("2.12" -> "0.2.8", "2.13" -> "0.2.8", "3" -> "0.2.8"),
  )
  .dependsOn(core)
