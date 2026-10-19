package com.dwolla.tracing

import scala.collection.immutable.ListSet

sealed trait XRayAnnotationKey

sealed trait ClientAccountId extends XRayAnnotationKey
sealed trait EndUserAccountId extends XRayAnnotationKey
sealed trait TransactionId extends XRayAnnotationKey
sealed trait EventBusMessageResult extends XRayAnnotationKey
sealed trait BatchJobName extends XRayAnnotationKey
sealed trait BatchJobRunId extends XRayAnnotationKey
sealed trait BatchPartitionId extends XRayAnnotationKey
sealed trait BatchUnitIndex extends XRayAnnotationKey

object XRayAnnotationKey {
  val clientAccountId: String with ClientAccountId = key("client.account.id")
  val endUserAccountId: String with EndUserAccountId = key("enduser.account.id")
  val transactionId: String with TransactionId = key("transaction.id")
  val eventBusMessageResult: String with EventBusMessageResult = key("event-bus.message-handling-result")
  val batchJobName: String with BatchJobName = key("batch.job.name")
  val batchJobRunId: String with BatchJobRunId = key("batch.job.run.id")
  val batchPartitionId: String with BatchPartitionId = key("batch.partition.id")
  val batchUnitIndex: String with BatchUnitIndex = key("batch.unit.index")

  // ListSet keeps declaration order, so the generated collector config stays stable as keys are added
  private lazy val registry = ListSet.newBuilder[String with XRayAnnotationKey]

  private def key[T <: XRayAnnotationKey](s: String): String with T = {
    val t = s"com.dwolla.$s".asInstanceOf[String with T]
    registry += t
    t
  }

  lazy val values: Set[String with XRayAnnotationKey] = registry.result()
}
