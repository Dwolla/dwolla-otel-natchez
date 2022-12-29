package com.dwolla.tracing

sealed abstract class DwollaEnvironment(val name: String) {
  def normalizedName: String = name.toLowerCase
}

object DwollaEnvironment {
  case object Local extends DwollaEnvironment("Local")
  case object DevInt extends DwollaEnvironment("DevInt")
  case object Uat extends DwollaEnvironment("Uat")
  case object Prod extends DwollaEnvironment("Prod")
  case object Sandbox extends DwollaEnvironment("Sandbox")

  def apply(env: String): Option[DwollaEnvironment] =
    resolveEnv.lift(env.toLowerCase)

  private val resolveEnv: PartialFunction[String, DwollaEnvironment] = {
    case "local" => Local
    case "devint" => DevInt
    case "uat" => Uat
    case "prod" | "production" => Prod
    case "sandbox" => Sandbox
  }
}
