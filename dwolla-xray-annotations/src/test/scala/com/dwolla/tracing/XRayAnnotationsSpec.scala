package com.dwolla.tracing

import munit.FunSuite

class XRayAnnotationsSpec extends FunSuite {
  test("no more than 50 XRayAnnotations exist") {
    assert(XRayAnnotationKey.values.size <= 50)
  }

  test("no more than 50 indexed attributes exist") {
    assert(IndexedAttributes.indexedAttributes.linesIterator.size <= 50)
  }

  test("client.account.id") {
    assertEquals(XRayAnnotationKey.clientAccountId, "com.dwolla.client.account.id")
  }

  test("enduser.account.id") {
    assertEquals(XRayAnnotationKey.endUserAccountId, "com.dwolla.enduser.account.id")
  }

  test("transaction.id") {
    assertEquals(XRayAnnotationKey.transactionId, "com.dwolla.transaction.id")
  }

  test("event-bus.message-handling-result") {
    assertEquals(XRayAnnotationKey.eventBusMessageResult, "com.dwolla.event-bus.message-handling-result")
  }

  test("batch.job.name") {
    assertEquals(XRayAnnotationKey.batchJobName, "com.dwolla.batch.job.name")
  }

  test("batch.job.run.id") {
    assertEquals(XRayAnnotationKey.batchJobRunId, "com.dwolla.batch.job.run.id")
  }

  test("batch.partition.id") {
    assertEquals(XRayAnnotationKey.batchPartitionId, "com.dwolla.batch.partition.id")
  }

  test("batch.unit.index") {
    assertEquals(XRayAnnotationKey.batchUnitIndex, "com.dwolla.batch.unit.index")
  }

  test("values lists the keys in declaration order, so the generated collector config is stable") {
    assertEquals(
      XRayAnnotationKey.values.toList,
      List(
        "com.dwolla.client.account.id",
        "com.dwolla.enduser.account.id",
        "com.dwolla.transaction.id",
        "com.dwolla.event-bus.message-handling-result",
        "com.dwolla.batch.job.name",
        "com.dwolla.batch.job.run.id",
        "com.dwolla.batch.partition.id",
        "com.dwolla.batch.unit.index"
      )
    )
  }
}

/**
 * Run this and put the output into `otel-config.yml` under
 * `exporters.awsxray.indexed_attributes`.
 */
object GenerateIndexedAttributes extends App {
  println(IndexedAttributes.indexedAttributes)
}
