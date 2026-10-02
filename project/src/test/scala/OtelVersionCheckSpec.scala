import munit.ScalaCheckSuite
import org.scalacheck.{Gen, Prop}
import org.scalacheck.Prop.propBoolean
import OtelVersionCheck._

class OtelVersionCheckSpec extends ScalaCheckSuite {

  private val version: Gen[String] =
    Gen.listOfN(3, Gen.choose(0, 200)).map(_.mkString("."))

  private def module(organization: String, name: String, revision: String): ResolvedModule =
    ResolvedModule(organization, name, revision, callers = List("com.example:foo-service:1.0.0"))

  property("modules at their organization's base version pass, stable or alpha") {
    Prop.forAll(version, version) { (otel, instrumentation) =>
      val resolved = List(
        module("io.opentelemetry", "opentelemetry-api", otel),
        module("io.opentelemetry", "opentelemetry-api-incubator", s"$otel-alpha"),
        module("io.opentelemetry.instrumentation", "opentelemetry-instrumentation-api", instrumentation),
        module("io.opentelemetry.instrumentation", "opentelemetry-runtime-telemetry", s"$instrumentation-alpha"),
      )
      val pins = Map("io.opentelemetry" -> otel, "io.opentelemetry.instrumentation" -> instrumentation)
      assertEquals(problems(pins, resolved), Nil)
    }
  }

  property("a stable module on any other version is reported, with what pulled it in") {
    Prop.forAll(version, version) { (pinned, stray) =>
      (pinned != stray) ==> {
        val strayModule = module("io.opentelemetry", "opentelemetry-exporter-otlp", stray)
        val resolved = List(module("io.opentelemetry", "opentelemetry-api", pinned), strayModule)
        assertEquals(problems(Map("io.opentelemetry" -> pinned), resolved), List(WrongVersion(strayModule, pinned)))
      }
    }
  }

  test("an alpha module lagging the stable line is reported") {
    val lagging = module("io.opentelemetry", "opentelemetry-api-incubator", "1.65.0-alpha")
    val resolved = List(module("io.opentelemetry", "opentelemetry-api", "1.66.0"), lagging)
    assertEquals(problems(Map("io.opentelemetry" -> "1.66.0"), resolved), List(WrongVersion(lagging, "1.66.0")))
  }

  test("io.opentelemetry.semconv is ignored") {
    val resolved = List(module("io.opentelemetry.semconv", "opentelemetry-semconv", "1.43.0"))
    assertEquals(problems(Map("io.opentelemetry" -> "1.66.0"), resolved), Nil)
  }

  test("an io.opentelemetry.* organization without a pin is reported") {
    val unpinned = module("io.opentelemetry.javaagent", "opentelemetry-javaagent-extension-api", "2.31.1-alpha")
    assertEquals(problems(Map("io.opentelemetry" -> "1.66.0"), List(unpinned)), List(UnpinnedOrganization(unpinned)))
  }

  test("modules outside the OpenTelemetry organizations are ignored") {
    val resolved = List(
      module("org.typelevel", "otel4s-oteljava_3", "1.1.0"),
      module("io.opentelemetryx", "lookalike", "9.9.9"),
    )
    assertEquals(problems(Map("io.opentelemetry" -> "1.66.0"), resolved), Nil)
  }

  test("a problem's description names the module, both versions, and its callers") {
    val stray = module("io.opentelemetry", "opentelemetry-exporter-otlp", "1.55.0")
    val description = WrongVersion(stray, "1.66.0").describe
    assert(description.contains("io.opentelemetry:opentelemetry-exporter-otlp:1.55.0"), description)
    assert(description.contains("1.66.0"), description)
    assert(description.contains("com.example:foo-service:1.0.0"), description)
  }

  test("a problem with no callers says so instead of dangling") {
    val orphan = ResolvedModule("io.opentelemetry", "opentelemetry-exporter-otlp", "1.55.0", callers = Nil)
    val description = WrongVersion(orphan, "1.66.0").describe
    assert(description.contains("no caller information available"), description)
    assert(!description.trim.endsWith("pulled in by:"), description)
  }

  property("callersByModule lists every dependent of each dependency, once") {
    Prop.forAll(version, version) { (a, b) =>
      val edges = List("x:root:1" -> s"x:a:$a", "x:root:1" -> s"x:a:$a", s"x:a:$a" -> s"x:b:$b")
      assertEquals(
        callersByModule(edges),
        Map(s"x:a:$a" -> List("x:root:1"), s"x:b:$b" -> List(s"x:a:$a")),
      )
    }
  }

}
