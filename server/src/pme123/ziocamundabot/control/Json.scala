package pme123.ziocamundabot.control

import play.api.libs.{json => j}
import zio.ZIO

trait Json extends Serializable {
  def json: Json.Service[Any]
}

object Json {

  trait Service[R] {

    def fromJsonString[T](jsonStr: String)(implicit reader: j.Reads[T]): ZIO[R, JsonException, T]

    def toJson[T](obj: T)(implicit reader: j.Writes[T]): ZIO[R, Nothing, j.JsValue]
  }

  trait Live extends Json {

    val json: Service[Any] = new Service[Any] {
      def fromJsonString[T](jsonStr: String)(implicit reader: j.Reads[T]): ZIO[Any, JsonException, T] =
        ZIO.fromEither(j.Json.parse(jsonStr).validate[T].asEither)
          .mapError {
            case Seq((_, errors)) => JsonException.JsonReadException(errors.mkString("\n"))
          }

      def toJson[T](obj: T)(implicit reader: j.Writes[T]): ZIO[Any, Nothing, j.JsValue] =
        ZIO.succeed(j.Json.toJson(obj))

    }
  }

}

sealed trait JsonException

object JsonException {

  case class JsonReadException(msg: String) extends JsonException

  case class JsonWriteException(msg: String) extends JsonException

}
