package pme123.ziocamundabot.telegram

import canoe.methods.messages.SendMessage
import canoe.methods.queries.AnswerCallbackQuery
import canoe.models.{InlineKeyboardButton, InlineKeyboardMarkup}
import canoe.syntax._
import pme123.ziocamundabot.register.callbackRegister.RegisterCallback
import pme123.ziocamundabot.telegram.BotException._
import pme123.ziocamundabot.telegram.canoeClient.CanoeTaskClient
import zio._

/**
  * Service to send Messages to the Bot. Uses Canoe implementation.
  */
object botMessageSender {
  type BotMessageSender = Has[Service]

  trait Service {
    def sendMessage(chatId: ChatId,
                    maybeRCs: Option[RegisterCallback],
                    msg: String): IO[SendMessageException, Unit]

    def sendImmediateAnswer(queryId: String,
                    msg: String): IO[SendMessageException, Unit]
  }

  type BotMessageSenderDeps = Has[CanoeTaskClient]

  def live: URLayer[BotMessageSenderDeps, BotMessageSender] =
    ZLayer.fromService[CanoeTaskClient, Service] { canoeClient =>
      new Service {

        implicit val canoeCl: CanoeTaskClient = canoeClient


        override def sendMessage(chatId: ChatId,
                                 maybeRCs: Option[RegisterCallback],
                                 msg: String): IO[SendMessageException, Unit] = {
          val replyMarkup = claimedCallback(maybeRCs)
          // val api = new ChatApi(PrivateChat(chatId, None, None, None))
          SendMessage(chatId, msg, replyMarkup = replyMarkup).call
            .mapError(t =>
              SendMessageException(s"Problem sending message $msg:\n${t.getMessage}"))
            .unit
        }

        def sendImmediateAnswer(queryId: String,
                                msg: String): IO[SendMessageException, Unit] = {
          AnswerCallbackQuery.notification(queryId, msg).call
            .mapError(t =>
              SendMessageException(s"Problem sending answer $msg:\n${t.getMessage}"))
            .unit
        }

        private def claimedCallback(maybeRegCallback: Option[RegisterCallback]): Option[InlineKeyboardMarkup] =

          maybeRegCallback map { case RegisterCallback(requestId, _, callback) =>
            InlineKeyboardMarkup.singleColumn(
              callback.controls.map { c =>
                InlineKeyboardButton.callbackData(
                  c.text,
                  CALLBACK_TAG + createCallbackIdent(requestId, c.ident)
                )
              })
          }
      }

    }
}


