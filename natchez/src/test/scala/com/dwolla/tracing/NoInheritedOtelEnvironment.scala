package com.dwolla.tracing

/**
 * Fails the suite before any test runs if its JVM inherited `OTEL_*` variables, e.g. from the shell that started sbt.
 * The SDK reads every one of them, so they would break assertions in confusing ways. Suites in build.sbt's forked
 * environment test groups set some on purpose, so they don't mix this in.
 *
 * Mixed into the default test group's suites that start an SDK; suites that never read the environment don't need it.
 */
trait NoInheritedOtelEnvironment extends munit.Suite {
  override def beforeAll(): Unit = {
    super.beforeAll()
    val inherited = sys.env.keySet.filter(_.startsWith("OTEL_")).toList.sorted
    if (inherited.nonEmpty)
      throw new IllegalStateException(
        s"${getClass.getName} must run without OTEL_* environment variables, which the SDK reads; unset ${inherited.mkString(", ")} and run the tests again"
      )
  }
}
