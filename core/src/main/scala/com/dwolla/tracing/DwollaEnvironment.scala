package com.dwolla.tracing

sealed abstract class DwollaEnvironment(val name: String) {
  def normalizedName: String = name.toLowerCase

  /**
   * The `deployment.environment.name` value Dwolla's infrastructure uses for this environment. It differs from
   * [[name]] only for `Prod`, which the infrastructure calls `Production`.
   */
  def deploymentEnvironmentName: String = name
}

object DwollaEnvironment {
  case object Local extends DwollaEnvironment("Local")
  case object DevInt extends DwollaEnvironment("DevInt")
  case object Uat extends DwollaEnvironment("Uat")
  case object Prod extends DwollaEnvironment("Prod") {
    override def deploymentEnvironmentName: String = "Production"
  }
  case object Sandbox extends DwollaEnvironment("Sandbox")
  case object Admin extends DwollaEnvironment("Admin")

  def apply(env: String): Option[DwollaEnvironment] =
    resolveEnv.lift(env.toLowerCase)

  private val resolveEnv: PartialFunction[String, DwollaEnvironment] = {
    case "local" => Local
    case "devint" => DevInt
    case "uat" => Uat
    case "prod" | "production" => Prod
    case "sandbox" => Sandbox
    case "admin" => Admin
  }
}
