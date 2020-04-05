package pme123.ziocamundabot

import play.api.libs.json.{Json, OFormat}

package object telegram {

  sealed trait BotException extends AppException

  object BotException {

    case class SendMessageException(msg: String, override val cause: Option[Throwable] = None) extends BotException

    case class BotClientException(msg: String, override val cause: Option[Throwable] = None) extends BotException

    case class BotServiceException(msg: String, override val cause: Option[Throwable] = None) extends BotException

    case class BotTemplateException(msg: String, override val cause: Option[Throwable] = None) extends BotException

    case class BotCallbackException(msg: String, override val cause: Option[Throwable] = None) extends BotException

  }

  type ChatUserOrGroup = String
  type ChatId = Long
  type BotTaskIdent = String

  val CALLBACK_TAG = "CALLBACK"


  case class BotTask(ident: BotTaskIdent, chatUserOrGroup: ChatUserOrGroup, msg: String, maybeCallback: Option[Callback]) {
  }

  object BotTask {

    implicit val jsonFormat: OFormat[BotTask] = Json.format[BotTask]
  }

  case class Callback(messageName: String, businessKey: String, controls: Seq[Control]) {

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
}
