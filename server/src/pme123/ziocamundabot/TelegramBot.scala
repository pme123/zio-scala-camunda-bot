package pme123.ziocamundabot

import cats.MonadError
import com.bot4s.telegram.api.declarative.{Callbacks, Commands}
import com.bot4s.telegram.clients.SttpClient
import com.bot4s.telegram.methods.SendMessage
import com.bot4s.telegram.models.{CallbackQuery, ChatType, InlineKeyboardButton, InlineKeyboardMarkup, Message, ChatId => TelegramChatId}
import com.softwaremill.sttp.SttpBackend
import pme123.ziocamundabot.bot._
import pme123.ziocamundabot.callbackRegister.RegisterCallback
import zio.{Queue, Task}

case class TelegramBot(queue: Queue[CallbackQuery],
                       registerChat: (Option[ChatUserOrGroup], ChatId) => Task[Unit],
                       backend: SttpBackend[Task, Nothing],
                       token: String,
                       telegramHost: String = "api.telegram.org"
                      )(implicit monadError: MonadError[Task, Throwable])
  extends Callbacks[Task]
    with Commands[Task] {

  override val monad: MonadError[Task, Throwable] = monadError

  implicit private val b: SttpBackend[Task, Nothing] = backend
  val client = new SttpClient[Task](token, telegramHost)

  import TelegramBot._

  def sendMessage(chatId: ChatId,
                  maybeRCs: Option[RegisterCallback],
                  msg: String
                 ): Task[Message] = {
    println(s"send Message $chatId")
    val replyMarkup = claimedCallback(maybeRCs)
    requestMsg(chatId, msg)(replyMarkup)
  }

  onCommand('register) { implicit msg =>
    for {
      result <- registerChat(maybeUserOrGroup(msg), msg.chat.id)
      _ <- handleReply(msg.source, s"Hello ${msg.from.map(_.firstName).getOrElse("")}!\n" + result).unit
    } yield ()
  }


  onCallbackWithTag(CALLBACK_TAG) { implicit cbq => // listens on all callbacks that START with TAG
    implicit val msg: Message = cbq.message.get
    for {
      _ <- reply("hello")
      _ <- queue.offer(cbq)
    } yield ()
  }

  private def requestMsg(chatId: ChatId, msg: String
                        )(replyMarkup: Option[(String, InlineKeyboardMarkup)]
                        ) = {
    request(SendMessage(
      TelegramChatId(chatId.toString),
      msg,
      replyMarkup = replyMarkup.map { case (_, markup) => markup }
    ))
  }

  private def claimedCallback(maybeRegCallback: Option[RegisterCallback]) =

    maybeRegCallback map { case RegisterCallback(requestId, _, callback) =>
      (requestId, InlineKeyboardMarkup.singleColumn(
        callback.controls.map { c =>
          InlineKeyboardButton.callbackData(
            c.text,
            CALLBACK_TAG + createCallbackIdent(requestId, c.ident)
          )
        }))
    }

  private def handleReply(chatId: ChatId, replyMsg: String
                         ): Task[Message] =
    request(SendMessage(
      chatId,
      replyMsg
    ))
}

object TelegramBot {
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
}