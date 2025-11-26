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
    assertEquals(XRayAnnotationKey.clientAccountId, "client.account.id")
  }

  test("enduser.account.id") {
    assertEquals(XRayAnnotationKey.endUserAccountId, "enduser.account.id")
  }

  test("transaction.id") {
    assertEquals(XRayAnnotationKey.transactionId, "transaction.id")
  }
}

/**
 * Run this and put the output into `otel-config.yml` under
 * `exporters.awsxray.indexed_attributes`.
 */
object GenerateIndexedAttributes extends App {
  println(IndexedAttributes.indexedAttributes)
}
