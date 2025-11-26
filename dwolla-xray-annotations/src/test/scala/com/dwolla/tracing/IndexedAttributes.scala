package com.dwolla.tracing

object IndexedAttributes {
  val indexedAttributes: String =
    ("deployment.environment.name" ::
      "otel.resource.deployment.environment.name" ::
      "otel.resource.service.name" ::
      XRayAnnotationKey.values.toList)
      .mkString("      - ", "\n      - ", "")
}
