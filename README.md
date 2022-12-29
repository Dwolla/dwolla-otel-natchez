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
