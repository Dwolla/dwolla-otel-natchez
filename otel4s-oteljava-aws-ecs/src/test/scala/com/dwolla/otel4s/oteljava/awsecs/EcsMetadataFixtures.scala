package com.dwolla.otel4s.oteljava.awsecs

object EcsMetadataFixtures {
  val ec2Container: String =
    """{
      |  "DockerId": "ea32192c8553fbff06c9340478a2ff089b2bb5646fb718b4ee206641c9086d66",
      |  "Name": "curl",
      |  "DockerName": "ecs-curltest-24-curl-cca48e8dcadd97805600",
      |  "Image": "111122223333.dkr.ecr.us-west-2.amazonaws.com/curltest:latest",
      |  "ImageID": "sha256:d691691e9652791a60114e67b365688d20d19940dde7c4736ea30e660d8d3553",
      |  "LogDriver": "awslogs",
      |  "LogOptions": {"awslogs-group": "/ecs/metadata", "awslogs-region": "us-west-2", "awslogs-stream": "ecs/curl/8f03e41243824aea923aca126495f665"},
      |  "ContainerARN": "arn:aws:ecs:us-west-2:111122223333:container/0206b271-b33f-47ab-86c6-a0ba208a70a9"
      |}""".stripMargin

  // EC2 launch type: no AvailabilityZone, and the cluster is a short name
  val ec2Task: String =
    """{
      |  "Cluster": "default",
      |  "TaskARN": "arn:aws:ecs:us-west-2:111122223333:task/default/158d1c8083dd49d6b527399fd6414f5c",
      |  "Family": "curltest",
      |  "Revision": "26",
      |  "LaunchType": "EC2"
      |}""".stripMargin

  val fargateContainer: String =
    """{
      |  "DockerId": "cd189a933e5849daa93386466019ab50-2495160603",
      |  "Name": "curl",
      |  "DockerName": "curl",
      |  "Image": "111122223333.dkr.ecr.us-west-2.amazonaws.com/curltest:1.2.3",
      |  "ImageID": "sha256:25f3695bedfb454a50f12d127839a68ad3caf91e451c1da073db34c542c4d2cb",
      |  "LogDriver": "splunk",
      |  "LogOptions": {"splunk-url": "https://splunk.example.com"},
      |  "ContainerARN": "arn:aws:ecs:us-west-2:111122223333:container/05966557-f16c-49cb-9352-24b3a0dcd0e1"
      |}""".stripMargin

  val fargateTask: String =
    """{
      |  "Cluster": "arn:aws:ecs:us-west-2:111122223333:cluster/default",
      |  "TaskARN": "arn:aws:ecs:us-west-2:111122223333:task/default/e9028f8d5d8e4f258373e7b93ce9a3c3",
      |  "Family": "curltest",
      |  "Revision": "3",
      |  "AvailabilityZone": "us-west-2d",
      |  "LaunchType": "FARGATE"
      |}""".stripMargin

  // an old agent: no LaunchType, LogDriver, LogOptions, or ContainerARN
  val minimalContainer: String =
    """{"DockerId": "abc", "Name": "curl", "DockerName": "ecs-curl", "Image": "curltest", "ImageID": ""}"""
  val minimalTask: String =
    """{"Cluster": "default", "TaskARN": "arn:aws:ecs:us-west-2:111122223333:task/default/0123", "Family": "curltest", "Revision": "1"}"""
}
