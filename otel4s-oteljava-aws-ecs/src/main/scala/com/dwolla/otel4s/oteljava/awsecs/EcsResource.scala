/*
 * Ported from opentelemetry-java-contrib's aws-resources EcsResource.java.
 * Original copyright notice:
 *
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dwolla.otel4s.oteljava.awsecs

import cats.effect.*
import cats.effect.instances.spawn.*
import cats.effect.syntax.all.*
import cats.effect.std.Env
import cats.syntax.all.*
import io.opentelemetry.api.common.{AttributeKey, Attributes, AttributesBuilder}
import io.opentelemetry.sdk.resources.Resource as OTResource
import org.http4s.Uri
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.Client

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

private[awsecs] object EcsResource {
  private[awsecs] val timeout: FiniteDuration = 2.seconds

  private val dockerImage = "^([^:@\\s]+(?::\\d+/[^:@\\s]+)?)(?::([^@\\s]+))?(@sha256:[\\da-fA-F]+)?$".r

  // task ARN: arn:<partition>:ecs:<region>:<account>:task/<cluster>/<task-id>
  private final case class TaskArn(partition: String, region: String, account: String, taskId: String)
  private def parseTaskArn(arn: String): Option[TaskArn] =
    arn.split(":", 6).toList match {
      case "arn" :: partition :: "ecs" :: region :: account :: resource :: Nil =>
        resource.split('/').lastOption.map(TaskArn(partition, region, account, _))
      case _ => None
    }

  /** Detects from `ECS_CONTAINER_METADATA_URI_V4`; the empty resource when it is unset or detection fails. */
  private[awsecs] def detect[F[_] : Temporal : Env](client: Client[F]): F[OTResource] =
    Env[F].get("ECS_CONTAINER_METADATA_URI_V4").flatMap {
      _.flatMap(Uri.fromString(_).toOption)
        .fold(OTResource.empty().pure[F])(fromMetadataUri(client, _))
    }

  private[awsecs] def fromMetadataUri[F[_] : Temporal](client: Client[F], containerMetadataUri: Uri): F[OTResource] =
    (client.expect[EcsContainerMetadata](containerMetadataUri), client.expect[EcsTaskMetadata](containerMetadataUri / "task"))
      .parTupled
      .map((toResource _).tupled)
      .timeout(timeout)
      .handleError(_ => OTResource.empty())

  private def toResource(container: EcsContainerMetadata, task: EcsTaskMetadata): OTResource = {
    val arn = parseTaskArn(task.taskArn)
    val clusterArn =
      if (task.cluster.startsWith("arn:")) task.cluster.some
      else arn.map(a => s"arn:${a.partition}:ecs:${a.region}:${a.account}:cluster/${task.cluster}")
    val image = dockerImage.findFirstMatchIn(container.image)
    val awslogs = container.logOptions.filter(_ => container.logDriver.contains("awslogs")).getOrElse(Map.empty)

    val strings: List[(String, Option[String])] = List(
      "cloud.provider" -> "aws".some,
      "cloud.platform" -> "aws_ecs".some,
      "cloud.region" -> arn.map(_.region),
      "cloud.account.id" -> arn.map(_.account),
      "cloud.availability_zone" -> task.availabilityZone,
      "cloud.resource_id" -> container.containerArn,
      "aws.ecs.task.arn" -> task.taskArn.some,
      "aws.ecs.task.id" -> arn.map(_.taskId),
      "aws.ecs.task.family" -> task.family.some,
      "aws.ecs.task.revision" -> task.revision.some,
      "aws.ecs.launchtype" -> task.launchType.map(_.toLowerCase(java.util.Locale.ROOT)),
      "aws.ecs.cluster.arn" -> clusterArn,
      "aws.ecs.container.arn" -> container.containerArn,
      "container.id" -> container.dockerId.some,
      "container.name" -> container.dockerName.some,
      "container.image.name" -> image.map(_.group(1)),
    )
    val lists: List[(String, Option[String])] = List(
      "container.image.tags" -> image.flatMap(m => Option(m.group(2)).orElse(Option.when(m.group(3) == null)("latest"))),
      "aws.log.group.names" -> awslogs.get("awslogs-group"),
      "aws.log.stream.names" -> awslogs.get("awslogs-stream"),
    )

    val withStrings = strings.foldLeft(Attributes.builder()) {
      case (b, (key, Some(value))) => b.put(AttributeKey.stringKey(key), value)
      case (b, _) => b
    }
    val withLists = lists.foldLeft(withStrings: AttributesBuilder) {
      case (b, (key, Some(value))) => b.put[java.util.List[String]](AttributeKey.stringArrayKey(key), List(value).asJava)
      case (b, _) => b
    }
    OTResource.create(withLists.build())
  }
}
