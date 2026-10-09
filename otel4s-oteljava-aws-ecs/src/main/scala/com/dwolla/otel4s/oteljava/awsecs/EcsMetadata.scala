package com.dwolla.otel4s.oteljava.awsecs

import io.circe.Decoder

/** The fields of an ECS task metadata v4 container response that we use; AWS documents the optional ones as conditional. */
private[awsecs] final case class EcsContainerMetadata(dockerId: String,
                                                      dockerName: String,
                                                      image: String,
                                                      logDriver: Option[String],
                                                      logOptions: Option[Map[String, String]],
                                                      containerArn: Option[String])

private[awsecs] object EcsContainerMetadata {
  implicit val decoder: Decoder[EcsContainerMetadata] =
    Decoder.forProduct6("DockerId", "DockerName", "Image", "LogDriver", "LogOptions", "ContainerARN")(EcsContainerMetadata.apply)
}

/** The fields of an ECS task metadata v4 task response that we use. */
private[awsecs] final case class EcsTaskMetadata(cluster: String,
                                                 taskArn: String,
                                                 family: String,
                                                 revision: String,
                                                 availabilityZone: Option[String],
                                                 launchType: Option[String])

private[awsecs] object EcsTaskMetadata {
  implicit val decoder: Decoder[EcsTaskMetadata] =
    Decoder.forProduct6("Cluster", "TaskARN", "Family", "Revision", "AvailabilityZone", "LaunchType")(EcsTaskMetadata.apply)
}
