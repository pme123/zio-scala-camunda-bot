package pme123.ziocamundabot.control

import com.softwaremill.sttp.playJson.{asJson, _}
import com.softwaremill.sttp.{DeserializationError, HttpURLConnectionBackend, Id, Response, SttpBackend, Uri, sttp, _}
import play.api.libs.json.{JsError, JsValue, Reads}
import pme123.ziocamundabot.control.CamundaException.ServiceException
import pme123.ziocamundabot.entity.camunda.{CompleteTask, ExternalTask, FetchAndLock, Signal}
import pme123.ziocamundabot.entity.configuration.CamundaConfig
import zio.console.Console
import zio.{IO, ZIO}


trait Camunda extends Serializable with Console {
  def camundaService: Camunda.Service[Console]
}

object Camunda {

  type CamundaEnv = Camunda with Register with Json

  trait Service[R <: Console] {
    def fetchAndLock(fetchAndLock: FetchAndLock): ZIO[R, CamundaException, Seq[ExternalTask]]

    def completeTask(completeTask: CompleteTask): ZIO[R, CamundaException, Unit]

    def signal(signal: Signal): ZIO[R, CamundaException, JsValue]
  }

  trait Live extends Camunda {

    def config: CamundaConfig

    type ResponseParser[C] = ResponseAs[Either[DeserializationError[JsError], C], Nothing]
    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

    val camundaService: Service[Console] = new Service[Console] {

      def fetchAndLock(fetchAndLock: FetchAndLock): ZIO[Console, CamundaException, Seq[ExternalTask]] =
        post(externalTaskUri,
          fetchAndLock,
          asJson[Seq[ExternalTask]]
        )

      def completeTask(completeTask: CompleteTask): ZIO[Console, CamundaException, Unit] =
        post(completeTaskUri(completeTask.taskId), completeTask)
          .map(_ => ())

      def signal(signal: Signal): ZIO[Console, CamundaException, JsValue] =
        post(signalUri, signal, asJson[JsValue])

      private def post[B: BodySerializer, C: Reads : IsOption](uri: Uri,
                                                               body: B,
                                                               responseParser: ResponseParser[C]) =
        for {
          r <- sttp
            .auth.basic(config.user, config.password)
            .body(body)
            .post(uri)
            .response(responseParser)
            .send()
            .handleResponse(uri)
          _ = println(s"POST: $uri")
          //  _ <- putStrLn(s"Result of $uri: $r")
        } yield r

      private def post[B: BodySerializer](uri: Uri,
                                                               body: B) =
        ZIO.fromEither(for {
          r <- sttp
            .auth.basic(config.user, config.password)
            .body(body)
            .post(uri)
            .response(ignore)
            .send()
            .body
          //  _ <- putStrLn(s"Result of $uri: $r")
        } yield r)
          .mapError(m => ServiceException(s"Problem calling $uri\n$m"))


      private lazy val baseUrl = s"${config.endpoint}:${config.port}/engine-rest"
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

}

sealed trait CamundaException

object CamundaException {

  case class ServiceException(msg: String) extends CamundaException

  case class JsonParseException(msg: String) extends CamundaException

  case class HandleTaskException(msg: String) extends CamundaException

}




