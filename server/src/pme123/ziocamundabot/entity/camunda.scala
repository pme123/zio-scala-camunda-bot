package pme123.ziocamundabot.entity

import play.api.libs.json.{Json, OFormat}

object camunda {
  case class FetchAndLock(
                           workerId: String,
                           topics: List[Topic],
                           maxTasks: Double = 1,
                           usePriority: Boolean = true,
                         )

  object FetchAndLock {

    implicit val jsonFormat: OFormat[FetchAndLock] = Json.format[FetchAndLock]
  }

  case class Topic(
                    topicName: String,
                    variables: Seq[String],
                    lockDuration: Double = 100000,
                  )

  object Topic {

    implicit val jsonFormat: OFormat[Topic] = Json.format[Topic]
  }

  case class ExternalTask(
                           activityId: String,
                           activityInstanceId: String,
                           errorMessage: Option[String],
                           executionId: String,
                           id: String,
                           lockExpirationTime: String,
                           processDefinitionId: String,
                           processDefinitionKey: String,
                           processInstanceId: String,
                           tenantId: Option[String],
                           retries: Option[Int],
                           workerId: String,
                           priority: Int,
                           topicName: String,
                           variables: Map[String, Variable]
                         )

  object ExternalTask {

    implicit val jsonFormat: OFormat[ExternalTask] = Json.format[ExternalTask]
  }

  case class Variable(
                       value: String,
                       `type`: String = "String",
                     )

  object Variable {

    implicit val jsonFormat: OFormat[Variable] = Json.format[Variable]
  }

  case class CompleteTask(
                           taskId: String,
                           workerId: String,
                           variables: Map[String, Variable] = Map.empty,
                         )

  object CompleteTask {

    implicit val jsonFormat: OFormat[CompleteTask] = Json.format[CompleteTask]
  }

  case class Signal(
                     name: String,
                     variables: Map[String, Variable] = Map.empty,
                   )

  object Signal {

    implicit val jsonFormat: OFormat[Signal] = Json.format[Signal]
  }

}
