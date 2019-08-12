package pme123.ziocamundabot.entity

import bot._

object register {

  case class RegisterChatId(maybeId: Option[ChatUserOrGroup], chatId: ChatId)

  case class RequestChatId(chatUserOrGroup: ChatUserOrGroup)

  case class RegisterCallback(botTaskIdent: String, userId: String, callback: Callback)

  case class OpenTasksRequest(maybeId: Option[ChatUserOrGroup])

  case class OpenTasks(maybeId: Option[ChatUserOrGroup], chatId: ChatId)

  object RegisterCallback {
    def apply(botTask: BotTask): Option[RegisterCallback] = {
      botTask.maybeCallback.map(callback =>
        RegisterCallback(botTask.ident, botTask.chatUserOrGroup, callback)
      )
    }
  }

  case class RequestCallback(callbackIdent: String) {
    val requestId: String = extractRequestId(callbackIdent)
    val callbackId: String = extractCallbackId(callbackIdent)
  }

  case class ResultCallback(botTaskIdent: String, signal: String, callbackId: String, response: String)

}