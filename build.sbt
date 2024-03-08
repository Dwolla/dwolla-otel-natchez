// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.3"

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

lazy val root = tlCrossRootProject.aggregate(
  core,
  `aws-xray-id-generator`,
)

lazy val core = project.in(file("core"))
  .settings(
    name := "dwolla-otel-natchez",
    description := "Utilities for configuring a Natchez EntryPoint for OpenTelemetry at Dwolla",
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % "0.3.5",
      "org.tpolecat" %% "natchez-opentelemetry" % "0.3.5",
      "org.typelevel" %% "cats-core" % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.3",
      "org.typelevel" %% "cats-effect-kernel" % "3.5.3",
      "org.typelevel" %% "cats-effect-std" % "3.5.3",
      "org.typelevel" %% "cats-mtl" % "1.4.0",
      "org.typelevel" %% "log4cats-core" % "2.6.0",
      "io.circe" %% "circe-literal" % "0.14.6",
      "org.typelevel" %% "jawn-parser" % "1.5.1" % Provided,
      "io.opentelemetry" % "opentelemetry-api" % "1.34.1",
      "io.opentelemetry" % "opentelemetry-context" % "1.34.1",
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.34.1",
      "io.opentelemetry" % "opentelemetry-extension-trace-propagators" % "1.34.1",
      "io.opentelemetry" % "opentelemetry-sdk" % "1.34.1",
      "io.opentelemetry" % "opentelemetry-sdk-common" % "1.34.1",
      "io.opentelemetry" % "opentelemetry-sdk-trace" % "1.34.1",
      "io.opentelemetry.semconv" % "opentelemetry-semconv" % "1.23.1-alpha",
      "io.opentelemetry.contrib" % "opentelemetry-aws-resources" % "1.32.0-alpha",
      "io.opentelemetry.contrib" % "opentelemetry-aws-xray-propagator" % "1.32.0-alpha",
    )
  )
  .dependsOn(`aws-xray-id-generator`)

lazy val `aws-xray-id-generator` = project
  .in(file("aws-xray-id-generator"))
  .settings(
    name := "otel-aws-xray-id-generator",
    description := "Generate OTel trace IDs compatible with AWS X-Ray with minimal dependencies",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "3.5.2",
      "io.opentelemetry" % "opentelemetry-api" % "1.33.0",
      "io.opentelemetry" % "opentelemetry-sdk-trace" % "1.33.0",
    )
  )
