package pme123.ziocamundabot.camunda

import play.api.libs.json._
import pme123.ziocamundabot.camunda.CamundaException.{JsonParseException, ServiceException}
import pme123.ziocamundabot.configuration.CamundaConfig
import sttpBackend.SttpTaskBackend
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.model.Uri
import zio.console.Console
import zio.{IO, Task, ZIO}

trait CamundaClient {
  def console: Console.Service

  def camundaConfig: Task[CamundaConfig]

  type ResponseParser[C] = ResponseAs[Either[ResponseError[JsError], C], Nothing]
  //ResponseAs[Either[DeserializationError[JsError],C],Nothing]

  implicit def backend: SttpTaskBackend

  private lazy val baseUrl = camundaConfig.map(c => s"${c.endpoint}:${c.port}/engine-rest")
  protected lazy val externalTaskUri: ZIO[Any, Throwable, Uri] = baseUrl.map(u => uri"$u/external-task/fetchAndLock")
  protected lazy val messageUri: ZIO[Any, Throwable, Uri] = baseUrl.map(u =>
    uri"$u/message")

  protected def completeTaskUri(taskId: String): ZIO[Any, Throwable, Uri] = baseUrl.map(u => uri"$u/external-task/$taskId/complete")


  protected def postWithResult[B: BodySerializer, C: Reads : IsOption](uriTask: Task[Uri],
                                                                       body: B,
                                                                       responseParser: ResponseParser[C]): IO[CamundaException, C] =
    (for {
      uri <- uriTask
      requestWithAuth <- requestWithAuth(uri, body)
      response <-
        requestWithAuth.response(responseParser)
          .send()
          .map(_.body)
          .flatMap {
            case Left(ex: HttpError) =>
              ZIO.fail(JsonParseException(s"Problem accessing the Service: $uri!\n" + ex.body))
            case Left(ex: DeserializationError[JsError]) =>
              ZIO.fail(JsonParseException(s"Problem parsing response!" + ex.error))
            case Right(value) =>
              ZIO.succeed(value)
          }
      _ <- console.putStrLn(s"Result of $uri: $response")
    } yield response) //TODO resilience Long polling
      .mapError {
        case ex: CamundaException => ex
        case ex =>
          ServiceException(s"There is an unknown Problem with the Camunda Service.", Some(ex))
      }

  protected def post[B: BodySerializer](uriTask: Task[Uri], body: B): IO[CamundaException, Unit] =
    (for {
      uri <- uriTask
      requestWithAuth <- requestWithAuth(uri, body)
      response <-
        requestWithAuth.response(ignore)
          .send()
          .map(_.body)
          .mapError(ex => ServiceException(s"Problem calling $uri\n$ex"))
      _ <- console.putStrLn(s"Result of $uri: $response")
    } yield response) //TODO resilience Long polling
      .mapError(ex => ServiceException(s"There is an unknown Problem with the Camunda Service.\n$ex"))


  private def requestWithAuth[B: BodySerializer](uri: Uri, body: B): IO[Throwable, Request[Either[String, String], Nothing]] =
    for {
      config <- camundaConfig
    } yield basicRequest
      .auth.basic(config.user, config.password)
      .body(body)
      .post(uri)
}
