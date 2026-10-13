# dwolla-otel-natchez

Provides `OpenTelemetryAtDwolla`, a utility object that configures a Natchez `EntryPoint[F]` for use with OpenTelemetry, using defaults appropriate for Dwolla tagless-final applications.

## Example Usage

```scala
import cats.effect.{Trace => _, _}
import com.dwolla.tracing.DwollaEnvironment.Local
import com.dwolla.tracing._

object MyApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    OpenTelemetryAtDwolla[IO]("example-app", args.headOption.flatMap(DwollaEnvironment(_)).getOrElse(Local))
      .use { entryPoint =>

        entryPoint.root("root span").use { span =>
          span.put("demo-type" -> "Hello World").as(ExitCode.Success)
        }
      }
  }
}
```

### Configuration with `OTEL_*`

The SDK is built with OpenTelemetry's [autoconfigure](https://opentelemetry.io/docs/languages/java/configuration/), so
the standard `OTEL_*` environment variables and `otel.*` system properties configure it: sampler, batch span processor,
exporter endpoint, and so on. With none set, the defaults are unchanged: OTLP over gRPC with gzip, a batch size of 128,
W3C, B3 multi-header, and X-Ray propagation, the `service.name`, `deployment.environment.name` (`Production` for
`Prod`), and `service.version` (when given) resource attributes, and X-Ray-compatible trace IDs (except through the
deprecated `Sync`-only `apply`). Autoconfigure also adds a per-JVM `service.instance.id` resource attribute
(OpenTelemetry Java 1.67.0 and later).

```
# record about 2% of root traces; children follow their parent's decision
OTEL_TRACES_SAMPLER=parentbased_traceidratio
OTEL_TRACES_SAMPLER_ARG=0.02

# batch span processor tuning
OTEL_BSP_MAX_QUEUE_SIZE=4096
```

- An application can ship its own sampler as a `ConfigurableSamplerProvider` (registered through the Java `ServiceLoader`
  SPI) and select it with `OTEL_TRACES_SAMPLER=<name>`.
- `OTEL_SERVICE_NAME`, or `service.name` in `OTEL_RESOURCE_ATTRIBUTES`, overrides the service name passed in code, as in
  the [otel4s](#otel4s) module.
- This module exports traces only. The metrics and logs exporters are always `none`, even if `OTEL_METRICS_EXPORTER` or
  `OTEL_LOGS_EXPORTER` is set, so it can run alongside `dwolla-otel4s-oteljava` (which handles metrics) in one JVM
  without exporting duplicate metrics.
- The SDK flushes pending spans and shuts down when the `EntryPoint` `Resource` is released.

## otel4s

`dwolla-otel4s-oteljava` configures [otel4s](https://typelevel.org/otel4s/) on the OpenTelemetry Java SDK with the
same Dwolla defaults: X-Ray-compatible trace IDs, W3C + B3 + X-Ray propagation, OTLP/gRPC with gzip, and
`service.name`, `service.version`, `deployment.environment.name` (in Dwolla's infrastructure spelling: `Production`
for `DwollaEnvironment.Prod`), and `service.instance.id` resource attributes. The OpenTelemetry SDK
(1.67.0 and later) generates `service.instance.id` as a random UUID per JVM, so every running instance writes its
own metric streams instead of colliding with its siblings'. Use it *instead of*
`dwolla-otel-natchez` when migrating from natchez to otel4s. Scala 2.13 and 3 only (otel4s doesn't publish for 2.12).

```scala
libraryDependencies += "com.dwolla" %% "dwolla-otel4s-oteljava" % "<version>"
```

`OtelJavaAtDwolla` needs an implicit `Random[F]` (for X-Ray-compatible trace IDs) and a `LocalContextProvider[F]`
in scope, in addition to `Async[F]`. `Random` is created effectfully, so for `IO` acquire it first, as the samples
below do.

Enable the signals you need; `build` returns exactly those providers (the tracer first when both are enabled), and
should be called once per application:

```scala
// Scala 3
import cats.effect.*
import cats.effect.std.Random
import com.dwolla.otel4s.oteljava.OtelJavaAtDwolla
import com.dwolla.tracing.DwollaEnvironment
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider

Random.scalaUtilRandom[IO].flatMap { implicit random =>
  OtelJavaAtDwolla[IO]("foo-service", BuildInfo.version, DwollaEnvironment.Local)
    .withTracing
    .withMetrics
    .build
    .use { case (given TracerProvider[IO], given MeterProvider[IO]) =>
      // otel4s-tagless and otel4s-smithy4s-metrics find their implicit providers here
      ???
    }
}
```

```scala
// Scala 2.13, with better-monadic-for (enabled by sbt-typelevel)
import cats.effect._
import cats.effect.std.Random
import com.dwolla.otel4s.oteljava.OtelJavaAtDwolla
import com.dwolla.tracing.DwollaEnvironment
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider

for {
  implicit0(random: Random[IO]) <- Random.scalaUtilRandom[IO].toResource
  (implicit0(tp: TracerProvider[IO]), implicit0(mp: MeterProvider[IO])) <-
    OtelJavaAtDwolla[IO]("foo-service", BuildInfo.version, DwollaEnvironment.Local).withTracing.withMetrics.build
  // ...
} yield ()
```

With one signal, `.use { implicit meterProvider => ... }` works on either version.

Every default is an overridable property: any `OTEL_*` environment variable or `otel.*` system property wins.
`OTEL_SERVICE_NAME` and a `service.name` in `OTEL_RESOURCE_ATTRIBUTES` both override the service name given to the
builder. `OTEL_RESOURCE_DISABLED_KEYS` removes any resource attribute, including Dwolla's `service.version` and
`deployment.environment.name` and the SDK's `service.instance.id`.

Options:

- `.withLoggedSpans` (requires `withTracing` and an implicit `LoggerFactory[F]`): also log each span as JSON, under
  the logger name `com.dwolla.otel4s.oteljava.LoggedSpans` (natchez's was the enclosing class name).
- `.registerGlobally`: also register the SDK as `GlobalOpenTelemetry`, for Java libraries that read it.
  `OpenTelemetryAtDwolla` always registered globally, but this module doesn't unless you call `.registerGlobally`,
  so Java libraries that read `GlobalOpenTelemetry` get a no-op otherwise. Acquisition fails if anything already
  registered one, or if anything already called `GlobalOpenTelemetry.get()` (OpenTelemetry Java 1.66 installs a
  no-op on the first call).
- `.withStartupHook(otel => resource)`: run something against the started SDK.
- `.withRuntimeMetrics` (requires `withMetrics`; add `dwolla-otel4s-oteljava-runtime-metrics` and
  `import com.dwolla.otel4s.oteljava.runtimemetrics._`): JVM runtime metrics.

### ECS resource attributes

`dwolla-otel4s-oteljava-aws-ecs` adds ECS resource attributes (`aws.ecs.task.arn`, `aws.ecs.task.id`,
`aws.ecs.cluster.arn`, `cloud.*`, `container.*`, and, with the `awslogs` driver, `aws.log.*`) detected from the ECS
task metadata endpoint at startup, through an http4s `Client[F]` you supply:

```scala
libraryDependencies += "com.dwolla" %% "dwolla-otel4s-oteljava-aws-ecs" % "<version>"
```

```scala
import com.dwolla.otel4s.oteljava.awsecs._

// requires an implicit LoggerFactory[IO], such as log4cats-slf4j's Slf4jFactory.create[IO]
OtelJavaAtDwolla[IO]("foo-service", BuildInfo.version, env)
  .withTracing
  .withEcsResource(httpClient)
  .build
```

`withEcsResource` adds the attributes to spans only. `.withEcsResourceOnMetrics(httpClient)` adds them to
metrics; to get both, call both. Both need an implicit log4cats `LoggerFactory[F]` (and an `Env[F]`) in scope.

Detection never fails startup. Outside ECS (`ECS_CONTAINER_METADATA_URI_V4` unset or malformed) it adds nothing and
logs nothing. Inside ECS, an HTTP error, an undecodable response, or a response that takes more than 2 seconds adds
nothing and logs one WARN, with the throwable, through your `LoggerFactory`. Each opt-in waits up to 2 seconds for
the metadata endpoint, so with both, startup can wait up to 4 seconds.

Detected attributes never replace the service name. Otherwise they override Dwolla's but not
`OTEL_RESOURCE_ATTRIBUTES`, and `OTEL_RESOURCE_DISABLED_KEYS` removes them.

Every resource attribute is sent with every metric data point. With CloudWatch metrics, which bill OTLP ingestion by
the byte, `withEcsResourceOnMetrics` adds cost for little value, since `service.instance.id` already keeps each
instance's series apart.

### Keep OpenTelemetry Java on one version

Mixing OpenTelemetry Java versions on one classpath fails at runtime with `NoClassDefFoundError`. This library's
artifacts are checked in CI to resolve a single line, but your application can still mix versions through other
dependencies. Declare the `io.opentelemetry` artifacts you use at one version.

## Contributing

`sbt test` includes an end-to-end test (module `otel4s-oteljava-e2e`, not published) that exports through the real
OTLP exporter to an OpenTelemetry Collector container, so it needs a running Docker daemon.
