package pme123.ziocamundabot.control

import com.bot4s.telegram.cats.{Polling, TelegramBot}
import com.bot4s.telegram.methods.SendMessage
import com.bot4s.telegram.models.{InlineKeyboardButton, InlineKeyboardMarkup, Message, ChatId => TelegramChatId}
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import pme123.ziocamundabot.entity.bot._
import pme123.ziocamundabot.entity.register.RegisterCallback
import zio.interop.catz._
import zio.{Task, ZIO}

import scala.language.higherKinds

trait Bot extends Serializable {

  def token: String

  def bot: Bot.Service[Any]
}

object Bot {

  trait Service[R] {
    def sendMessage(chatId: ChatId,
                    maybeRCs: Option[RegisterCallback],
                    msg: String
                   ): ZIO[R, Throwable, Message]
  }

  trait Live extends Bot {

    val bot: Service[Any] = new Service[Any] {

      private lazy val myBot = new MyBot

      def sendMessage(chatId: ChatId,
                      maybeRCs: Option[RegisterCallback],
                      msg: String
                     ): Task[Message] =
        myBot.sendMessage(chatId, maybeRCs, msg)
    }

    class MyBot
      extends TelegramBot[Task](token, AsyncHttpClientCatsBackend())
        with Polling[Task] {

      def sendMessage(chatId: ChatId,
                      maybeRCs: Option[RegisterCallback],
                      msg: String
                     ): Task[Message] =
        for {
          replyMarkup <- claimedCallback(maybeRCs)
          botMsg <- requestMsg(chatId, msg)(replyMarkup)
        } yield botMsg

      private def requestMsg(chatId: ChatId, msg: String
                            )(replyMarkup: Option[(String, InlineKeyboardMarkup)]
                            ): Task[Message] =
        request(SendMessage(
          TelegramChatId(chatId.toString),
          msg,
          replyMarkup = replyMarkup.map { case (_, markup) => markup }
        ))

      private def claimedCallback(maybeRegCallback: Option[RegisterCallback]): Task[Option[(String, InlineKeyboardMarkup)]] =
        Task {
          maybeRegCallback map { case RegisterCallback(requestId, _, callback) =>
            (requestId, InlineKeyboardMarkup.singleColumn(
              callback.controls.map { c =>
                InlineKeyboardButton.callbackData(
                  c.text,
                  CALLBACK_TAG + createCallbackIdent(requestId, c.ident)
                )
              }))
          }
        }
    }

  }

}

sealed trait BotException

object BotException {

  case class BotReadException(msg: String) extends BotException

  case class BotWriteException(msg: String) extends BotException

}
