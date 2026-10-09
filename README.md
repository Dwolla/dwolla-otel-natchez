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

## otel4s

`dwolla-otel4s-oteljava` configures [otel4s](https://typelevel.org/otel4s/) on the OpenTelemetry Java SDK with the
same Dwolla defaults: X-Ray-compatible trace IDs, W3C + B3 + X-Ray propagation, OTLP/gRPC with gzip, and
`service.name`, `service.version`, `deployment.environment.name` (in Dwolla's infrastructure spelling: `Production`
for `DwollaEnvironment.Prod`), and `service.instance.id` resource attributes. `service.instance.id` is a random UUID
for each SDK start, so every running instance writes its own metric streams instead of colliding with its
siblings'. Use it *instead of*
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
builder. `OTEL_RESOURCE_DISABLED_KEYS` can't remove `service.version`, `deployment.environment.name`, or
`service.instance.id`, which Dwolla always merges into the resource.

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

OtelJavaAtDwolla[IO]("foo-service", BuildInfo.version, env)
  .withTracing
  .withEcsResource(httpClient)
  .build
```

Detection never fails startup. Outside ECS, or if the endpoint errors or takes more than 2 seconds, it adds
nothing. Configured `OTEL_RESOURCE_ATTRIBUTES` win, and `OTEL_RESOURCE_DISABLED_KEYS` removes detected keys.

`withEcsResource` adds the attributes to spans only. `.withEcsResourceOnMetrics(httpClient)` also adds them to
metrics, but every resource attribute is sent with every metric data point. With CloudWatch metrics, which bill
OTLP ingestion by the byte, that adds cost for little value, since `service.instance.id` already keeps each
instance's series apart.

### Keep OpenTelemetry Java on one version

Mixing OpenTelemetry Java versions on one classpath fails at runtime with `NoClassDefFoundError`. This library's
artifacts are checked in CI to resolve a single line, but your application can still mix versions through other
dependencies. Declare the `io.opentelemetry` artifacts you use at one version.

## Contributing

`sbt test` includes an end-to-end test (module `otel4s-oteljava-e2e`, not published) that exports through the real
OTLP exporter to an OpenTelemetry Collector container, so it needs a running Docker daemon.
