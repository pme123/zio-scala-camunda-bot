package pme123.ziocamundabot.entity

import com.bot4s.telegram.models.{ChatType, Message}
import play.api.libs.json.{Json, OFormat}

object bot {

  type ChatUserOrGroup = String
  type ChatId = Long
  type BotTaskIdent = String

  val CALLBACK_TAG = "CALLBACK"

  def createCallbackIdent(requestId: String, callbackId: String) =
    s"$requestId--$callbackId"

  def extractCallbackIdent(callbackIdent: String): (String, String) = {
    val requestIdStr :: callbackId :: Nil = callbackIdent.split("--").toList
    (requestIdStr, callbackId)
  }

  def extractCallbackId(callbackIdent: String): String =
    extractCallbackIdent(callbackIdent)._2

  def extractRequestId(callbackIdent: String): String =
    extractCallbackIdent(callbackIdent)._1

  def maybeUserOrGroup(msg: Message): Option[String] = {
    msg.chat.`type` match {
      case ChatType.Private =>
        msg.chat.username
      case ChatType.Group =>
        msg.chat.title
    }
  }

  case class BotTask(ident: BotTaskIdent, chatUserOrGroup: ChatUserOrGroup, msg: String, maybeCallback: Option[Callback]) {
  }

  object BotTask {

    implicit val jsonFormat: OFormat[BotTask] = Json.format[BotTask]
  }

  case class Callback(signal: String, controls: Seq[Control]) {

  }

  object Callback {
    implicit val jsonFormat: OFormat[Callback] = Json.format[Callback]
  }

  case class Control(ident: String, text: String, response: String) {

  }

  object Control {
    implicit val jsonFormat: OFormat[Control] = Json.format[Control]
  }

  case class BotTaskResult(botTaskIdent: String, callbackIdent: String, from: User) {
  }

  object BotTaskResult {

    implicit val jsonFormat: OFormat[BotTaskResult] = Json.format[BotTaskResult]
  }

  case class User(id: Int, firstName: String, lastName: Option[String], username: String) {

  }

  object User {

    implicit val jsonFormat: OFormat[User] = Json.format[User]
  }

  case class Receipt(value: Map[String, Option[Any]]) {
    self =>
    final def |+|(that: Receipt): Receipt =
      Receipt(self.value ++ that.value)

    final def succeeded: Int = value.values.count(_.isEmpty)

    final def failures: List[Any] =
      value.values.collect { case Some(t) => t }.toList
  }

  object Receipt {
    def empty: Receipt = Receipt(Map())

    def success(id: String): Receipt = Receipt(Map(id -> None))

    def failure(id: String, t: Any): Receipt =
      Receipt(Map(id -> Some(t)))
  }

}