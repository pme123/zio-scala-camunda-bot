package pme123.ziocamundabot.control

import com.bot4s.telegram.api.declarative.{Callbacks, Commands}
import com.bot4s.telegram.methods.{AnswerCallbackQuery, SendMessage}
import com.bot4s.telegram.models
import com.bot4s.telegram.models.{CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup, Message, ChatId => TelegramChatId}
import com.softwaremill.sttp.asynchttpclient.zio.AsyncHttpClientZioBackend
import pme123.ziocamundabot.control.Bot.BotEnv
import pme123.ziocamundabot.control.BotException.{BotCallbackException, BotServiceException}
import pme123.ziocamundabot.control.camunda._
import pme123.ziocamundabot.control.json._
import pme123.ziocamundabot.control.register._
import pme123.ziocamundabot.entity
import pme123.ziocamundabot.entity.bot._
import pme123.ziocamundabot.entity.camunda.{Signal, Variable}
import pme123.ziocamundabot.entity.register.{RegisterCallback, ResultCallback}
import zio.interop.catz._
import zio.{Queue, Task, ZIO}

import scala.language.higherKinds

trait Bot extends Serializable with Register with Json with Camunda {

  def token: String

  def botService: Bot.Service[BotEnv]
}

object Bot {

  trait Service[R <: BotEnv] {

    def sendMessage(chatId: ChatId,
                    maybeRCs: Option[RegisterCallback],
                    msg: String
                   ): ZIO[R, BotException, Message]

    def initBot(): ZIO[R, BotException, Unit]
  }

  type BotEnv = Camunda with Json with Register

  trait Live extends Bot {

    val botService: Service[BotEnv] = new Service[BotEnv] {

      def initBot(): ZIO[BotEnv, BotException, Unit] = myBot.unit

      private lazy val myBot: ZIO[BotEnv, BotException, MyBot] =
        for {
          queue <- Queue.bounded[CallbackQuery](1000)
          rts <- ZIO.runtime[BotEnv]
          bot = new MyBot(
            cbq => queue.offer(cbq).unit,
            (maybeId: Option[ChatUserOrGroup], chatId: ChatId) =>
              Task.effect(rts.unsafeRun(registerChat(maybeId, chatId).unit))
          )
          _ <- listen(queue).fork
          _ <- bot.startPolling().mapError(e => BotServiceException("Problem polling", e))
        } yield bot

      def listen(queue: Queue[CallbackQuery]): ZIO[Camunda with Json with Register, BotException, Unit] =
        for {
          _ <- queue.take
            .flatMap(handleCallback1).forever
            .mapError(q => BotCallbackException(s"Problem take from queue: $q"))
        } yield ()

      def sendMessage(chatId: ChatId,
                      maybeRCs: Option[RegisterCallback],
                      msg: String
                     ): ZIO[BotEnv, BotException, Message] = {
        myBot.flatMap(_.sendMessage(chatId, maybeRCs, msg))
          .mapError { case e: Throwable =>
            BotServiceException(s"Problem Sending message: ${e.getMessage}", e)
          }
      }

      private def user(botUser: models.User): User =
        User(botUser.id, botUser.firstName, botUser.lastName, botUser.username.getOrElse("--"))

      private def handleCallback1(cbq: CallbackQuery,
                                 ): ZIO[BotEnv, BotCallbackException, Unit] =
        (for {
          b <- myBot
          _ <- ZIO.effect(b.request(AnswerCallbackQuery(cbq.id, Some("processing..."))))
          maybeRC <- requestCallback(cbq.data.getOrElse("---"))
          _ <- handleCallback(cbq, maybeRC)
          _ <- maybeRC.map { regCallback =>
            val botTaskResult = BotTaskResult(regCallback.botTaskIdent, regCallback.callbackId, user(cbq.from))
            for {
              json <- toJson(botTaskResult)
              _ <- signal(
                Signal(regCallback.signal,
                  Map("botTaskResult" -> Variable(json.toString)))
              )
            } yield ()
          }.getOrElse(ZIO.succeed("ok"))

        } yield ())
          .mapError(f => BotCallbackException(s"Problem handle Callback: $f"))


      private def handleCallback(cbq: CallbackQuery,
                                 maybeRC: Option[ResultCallback],
                                ) =
        ZIO.collectAll {
          (for {
            callbackIdent <- cbq.data
            msg <- cbq.message
          } yield {
            for {
              mbot <- myBot
              _ <- mbot.request(
                SendMessage(
                  msg.source,
                  maybeRC match {
                    case Some(regCallback: ResultCallback) =>
                      TextTemplateEngine.generate(cbq.from.username.get, regCallback.response, regCallback.botTaskIdent)
                    case None =>
                      s"Sorry, this issue (${entity.bot.extractRequestId(callbackIdent)}) was claimed already!"
                  })
              )
            } yield ()
          }).toList
        }
    }

    /*    }.forever
          .flatMap[Any, Throwable, Message](zio.stream.Stream.fromIterable)
      */

    private class MyBot(callback: CallbackQuery => Task[Unit],
                        registerChat: (Option[ChatUserOrGroup], ChatId) => Task[Unit]
                       )
      extends TelegramBot(token, AsyncHttpClientZioBackend())
        with Polling
        with Callbacks[Task]
        with Commands[Task] {

      def sendMessage(chatId: ChatId,
                      maybeRCs: Option[RegisterCallback],
                      msg: String
                     ): Task[Message] = {
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
          _ <- callback(cbq)
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

  }

}

sealed trait BotException

object BotException {

  case class BotServiceException(msg: String, cause: Throwable) extends BotException

  case class BotCallbackException(msg: String) extends BotException

}
