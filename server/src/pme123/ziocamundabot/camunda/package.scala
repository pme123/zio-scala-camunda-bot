package pme123.ziocamundabot

import play.api.libs.json.{Json, OFormat}

import scala.util.Random

package object camunda {

  sealed trait CamundaException extends AppException

  object CamundaException {

    case class ServiceException(msg: String, override val cause: Option[Throwable] = None) extends CamundaException

    case class JsonParseException(msg: String, override val cause: Option[Throwable] = None) extends CamundaException

    case class HandleTaskException(msg: String, override val cause: Option[Throwable] = None) extends CamundaException

  }

  case class FetchAndLock(
                           workerId: String,
                           topics: List[Topic],
                           maxTasks: Double = 1,
                           usePriority: Boolean = true,
                           asyncResponseTimeout: Int = 30 * 1000,
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

  case class CamMessage(
                         messageName: String,
                         businessKey: String,
                         processVariables: Map[String, Variable] = Map.empty,
                         resultEnabled: Boolean = true,
                       )

  object CamMessage {

    def issue(issue: String, businessKey: String = createBusinessKey): CamMessage =
      CamMessage("taskStart", businessKey, Map("issueIdent" -> Variable(businessKey), "issue" -> Variable(issue)))

    private def  createBusinessKey =
      s"issue-${Random.nextInt(899) + 100}"

    implicit val jsonFormat: OFormat[CamMessage] = Json.format[CamMessage]
  }
}
