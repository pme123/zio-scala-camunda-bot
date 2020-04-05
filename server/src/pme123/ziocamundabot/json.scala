package pme123.ziocamundabot

import play.api.libs.{json => j}
import zio._

/**
  * Marshalling and Unmarshalling JSON for Telegram API and Camunda API.
  * It uses play-json.
  */
object json {
  type Json = Has[Service]

  trait Service {

    def fromJsonString[T](jsonStr: String)(implicit reader: j.Reads[T]): IO[JsonException, T]

    def toJson[T](obj: T)(implicit reader: j.Writes[T]): IO[Nothing, j.JsValue]
  }

  def fromJsonString[T](jsonStr: String)(implicit reader: j.Reads[T]): ZIO[Json, JsonException, T] =
    ZIO.accessM(_.get.fromJsonString(jsonStr))

  def toJson[T](obj: T)(implicit reader: j.Writes[T]): ZIO[Json, Nothing, j.JsValue] =
    ZIO.accessM(_.get.toJson(obj))

  def live: ULayer[Json] = ZLayer.succeed(new Service {
    def fromJsonString[T](jsonStr: String)(implicit reader: j.Reads[T]): ZIO[Any, JsonException, T] =
      ZIO.fromEither(j.Json.parse(jsonStr).validate[T].asEither)
        .mapError {
          case Seq((_, errors)) => JsonException.JsonReadException(errors.mkString("\n"))
        }

    def toJson[T](obj: T)(implicit reader: j.Writes[T]): ZIO[Any, Nothing, j.JsValue] =
      ZIO.succeed(j.Json.toJson(obj))

  })

  sealed trait JsonException extends AppException

  object JsonException {

    case class JsonReadException(msg: String) extends JsonException

    case class JsonWriteException(msg: String) extends JsonException

  }

}
