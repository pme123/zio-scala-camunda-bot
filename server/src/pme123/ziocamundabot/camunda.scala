package pme123.ziocamundabot

import com.softwaremill.sttp.playJson.{asJson, _}
import com.softwaremill.sttp.{DeserializationError, HttpURLConnectionBackend, Id, Response, SttpBackend, Uri, sttp, _}
import play.api.libs.json._
import pme123.ziocamundabot.camunda.CamundaException.ServiceException
import pme123.ziocamundabot.configuration.CamundaConfig
import zio._
import zio.console.Console

object camunda {

  type CamundaEnv = Console

  type Camunda = Has[Service]


  trait Service {

    def fetchAndLock(fetchAndLock: FetchAndLock): IO[CamundaException, Seq[ExternalTask]]

    def completeTask(completeTask: CompleteTask): IO[CamundaException, Unit]

    def signal(signal: Signal): IO[CamundaException, JsValue]
  }

  def fetchAndLock(fetchAndLock: FetchAndLock): ZIO[Camunda, CamundaException, Seq[ExternalTask]] =
    ZIO.accessM(_.get.fetchAndLock(fetchAndLock))

  def completeTask(completeTask: CompleteTask): ZIO[Camunda, CamundaException, Unit] =
    ZIO.accessM(_.get.completeTask(completeTask))

  def signal(signal: Signal): ZIO[Camunda, CamundaException, JsValue] =
    ZIO.accessM(_.get.signal(signal))


  def live(camundaConfig: CamundaConfig): ZLayer[CamundaEnv, Nothing, Camunda] = ZLayer.fromFunction { ce: CamundaEnv =>
    val console = ce.get[Console.Service]
    type ResponseParser[C] = ResponseAs[Either[DeserializationError[JsError], C], Nothing]
    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

    new Service {

      def fetchAndLock(fetchAndLock: FetchAndLock): IO[CamundaException, Seq[ExternalTask]] =
        post(externalTaskUri,
          fetchAndLock,
          asJson[Seq[ExternalTask]]
        )

      def completeTask(completeTask: CompleteTask): IO[CamundaException, Unit] =
        post(completeTaskUri(completeTask.taskId), completeTask)
          .unit

      def signal(signal: Signal): IO[CamundaException, JsValue] =
        post(signalUri, signal, asJson[JsValue])

      private def post[B: BodySerializer, C: Reads : IsOption](uri: Uri,
                                                               body: B,
                                                               responseParser: ResponseParser[C]): ZIO[Any, CamundaException, C] =
        for {
          r <- sttp
            .auth.basic(camundaConfig.user, camundaConfig.password)
            .body(body)
            .post(uri)
            .response(responseParser)
            .send()
            .handleResponse(uri)
          _ <- console.putStrLn(s"Result of $uri: $r")
        } yield r

      private def post[B: BodySerializer](uri: Uri, body: B): ZIO[Any, CamundaException, Unit] =
        (for {
          r <- ZIO.fromEither(
            sttp
              .auth.basic(camundaConfig.user, camundaConfig.password)
              .body(body)
              .post(uri)
              .response(ignore)
              .send()
              .body)
          _ <- console.putStrLn(s"Result of $uri: $r")
        } yield r)
          .mapError(m => ServiceException(s"Problem calling $uri\n$m"))

      private lazy val baseUrl = s"${camundaConfig.endpoint}:${camundaConfig.port}/engine-rest"
      private lazy val externalTaskUri = uri"$baseUrl/external-task/fetchAndLock"
      private lazy val signalUri = uri"$baseUrl/signal"

      private def completeTaskUri(taskId: String) =
        uri"$baseUrl/external-task/$taskId/complete"

    }
  }

  type CamundaResponse[T] = Id[Response[Either[DeserializationError[JsError], T]]]

  implicit class ZioResponse[T](val response: Id[Response[Either[DeserializationError[JsError], T]]]) extends AnyVal {

    import CamundaException._

    def handleResponse(uri: Uri): IO[CamundaException, T] = {
      response.body match {
        case Left(ex: String) =>
          ZIO.fail(ServiceException(s"Problem calling $uri\n$ex"))
        case Right(Left(ex)) =>
          ZIO.fail(JsonParseException(s"Problem parsing response!" + ex.error.errors.mkString("\n - ", " - ", "\n")))
        case Right(Right(result)) =>
          ZIO.succeed(result)
      }
    }
  }

  sealed trait CamundaException extends AppException

  object CamundaException {

    case class ServiceException(msg: String) extends CamundaException

    case class JsonParseException(msg: String) extends CamundaException

    case class HandleTaskException(msg: String) extends CamundaException

  }

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
