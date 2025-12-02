package com.dwolla.tracing

sealed trait XRayAnnotationKey

sealed trait ClientAccountId extends XRayAnnotationKey
sealed trait EndUserAccountId extends XRayAnnotationKey
sealed trait TransactionId extends XRayAnnotationKey
sealed trait EventBusMessageResult extends XRayAnnotationKey

object XRayAnnotationKey {
  val clientAccountId: String with ClientAccountId = key("client.account.id")
  val endUserAccountId: String with EndUserAccountId = key("enduser.account.id")
  val transactionId: String with TransactionId = key("transaction.id")
  val eventBusMessageResult: String with EventBusMessageResult = key("event-bus.message-handling-result")

  private lazy val registry = Set.newBuilder[String with XRayAnnotationKey]

  private def key[T <: XRayAnnotationKey](s: String): String with T = {
    val t = s"com.dwolla.$s".asInstanceOf[String with T]
    registry += t
    t
  }

  lazy val values: Set[String with XRayAnnotationKey] = registry.result()
}
