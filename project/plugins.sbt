addSbtPlugin("org.typelevel" % "sbt-typelevel-ci-release" % "0.8.7")
addSbtPlugin("org.typelevel" % "sbt-typelevel-settings" % "0.8.7")
addSbtPlugin("org.typelevel" % "sbt-typelevel-mergify" % "0.8.7")

// Tests for the build's own code under project/ (run with: sbt "reload plugins" test "reload return")
libraryDependencies += "org.scalameta" %% "munit-scalacheck" % "1.3.1" % Test
