package com.dwolla.tracing

sealed trait XRayAnnotationKey

sealed trait ClientAccountId extends XRayAnnotationKey
sealed trait EndUserAccountId extends XRayAnnotationKey
sealed trait TransactionId extends XRayAnnotationKey

object XRayAnnotationKey {
  val clientAccountId: String with ClientAccountId = key("client.account.id")
  val endUserAccountId: String with EndUserAccountId = key("enduser.account.id")
  val transactionId: String with TransactionId = key("transaction.id")

  private lazy val registry = Set.newBuilder[String with XRayAnnotationKey]

  private def key[T <: XRayAnnotationKey](s: String): String with T = {
    val t = s.asInstanceOf[String with T]
    registry += t
    t
  }

  lazy val values: Set[String with XRayAnnotationKey] = registry.result()
}
