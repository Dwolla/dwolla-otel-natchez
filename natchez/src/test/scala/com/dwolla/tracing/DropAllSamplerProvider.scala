package com.dwolla.tracing

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSamplerProvider
import io.opentelemetry.sdk.trace.samplers.Sampler

/** An application-supplied sampler, loaded through autoconfigure's SPI and selected with `otel.traces.sampler`. */
class DropAllSamplerProvider extends ConfigurableSamplerProvider {
  override def createSampler(config: ConfigProperties): Sampler = Sampler.alwaysOff()
  override def getName: String = DropAllSamplerProvider.name
}

object DropAllSamplerProvider {
  val name: String = "dwolla-test-drop-all"
}
