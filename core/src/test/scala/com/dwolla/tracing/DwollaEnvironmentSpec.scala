package com.dwolla.tracing

import munit.FunSuite

class DwollaEnvironmentSpec extends FunSuite {
  private val expectedDeploymentEnvironmentNames: List[(DwollaEnvironment, String)] = List(
    DwollaEnvironment.Local -> "Local",
    DwollaEnvironment.DevInt -> "DevInt",
    DwollaEnvironment.Uat -> "Uat",
    DwollaEnvironment.Prod -> "Production",
    DwollaEnvironment.Sandbox -> "Sandbox",
    DwollaEnvironment.Admin -> "Admin",
  )

  expectedDeploymentEnvironmentNames.foreach { case (environment, expected) =>
    test(s"${environment.name} has deploymentEnvironmentName $expected") {
      assertEquals(environment.deploymentEnvironmentName, expected)
    }
  }

  test("Prod keeps its name and normalizedName") {
    assertEquals(DwollaEnvironment.Prod.name, "Prod")
    assertEquals(DwollaEnvironment.Prod.normalizedName, "prod")
  }
}
