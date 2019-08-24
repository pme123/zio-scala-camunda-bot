package pme123.ziocamundabot.control

import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.{Callbacks, Commands}
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.methods.SendMessage
import com.bot4s.telegram.models.{InlineKeyboardButton, InlineKeyboardMarkup, Message, ChatId => TelegramChatId}
import com.softwaremill.sttp.testing.SttpBackendStub
import pme123.ziocamundabot.control.BotException.BotServiceException
import pme123.ziocamundabot.entity.bot._
import pme123.ziocamundabot.entity.register.RegisterCallback
import zio.{Task, ZIO}

import scala.concurrent.Future
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
                   ): ZIO[R, BotException, Message]
  }

  trait Live extends Bot {

    val bot: Service[Any] = new Service[Any] {

      private lazy val myBot = new MyBot

      def sendMessage(chatId: ChatId,
                      maybeRCs: Option[RegisterCallback],
                      msg: String
                     ): ZIO[Any, BotException, Message] =
        myBot.sendMessage(chatId, maybeRCs, msg)
    }

    class MyBot
      extends TelegramBot
        with Polling
        with Callbacks[Future]
        with Commands[Future] {

      implicit val backend: SttpBackendStub[Future, Nothing] = SttpBackendStub.asynchronousFuture

      override implicit val client: RequestHandler[Future] = new FutureSttpClient(token)

      def sendMessage(chatId: ChatId,
                      maybeRCs: Option[RegisterCallback],
                      msg: String
                     ): ZIO[Any, BotException, Message] =
        for {
          replyMarkup <- claimedCallback(maybeRCs)
          botMsg <- requestMsg(chatId, msg)(replyMarkup)
        } yield botMsg

      onCallbackWithTag(CALLBACK_TAG) { implicit cbq => // listens on all callbacks that START with TAG
        Future(println("callback called"))
      }

      private def requestMsg(chatId: ChatId, msg: String
                            )(replyMarkup: Option[(String, InlineKeyboardMarkup)]
                            ): ZIO[Any, BotException, Message] =
        Task.fromFuture { implicit ex =>
          println("before request")
          val r = request(SendMessage(
            TelegramChatId(chatId.toString),
            msg,
            replyMarkup = replyMarkup.map { case (_, markup) => markup }
          ))
          println("after request")
          r
        }.mapError { exc =>
          exc.printStackTrace()
          BotServiceException(s"Problem calling Telegram API: ${exc.getMessage}", exc)
        }

      private def claimedCallback(maybeRegCallback: Option[RegisterCallback]): ZIO[Any, BotException, Option[(String, InlineKeyboardMarkup)]] =
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
        }.mapError {
          e => BotServiceException(s"Problem claiming Callback ${e.getMessage}", e)
        }
    }

  }

}

sealed trait BotException

object BotException {

  case class BotServiceException(msg: String, cause: Throwable) extends BotException

  case class BotReadException(msg: String) extends BotException

  case class BotWriteException(msg: String) extends BotException

}
