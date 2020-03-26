package pme123.ziocamundabot

import com.bot4s.telegram.methods.{AnswerCallbackQuery, SendMessage}
import com.bot4s.telegram.models
import com.bot4s.telegram.models._
import play.api.libs.json.{Json, OFormat}
import pme123.ziocamundabot.bot.BotException.{BotCallbackException, BotServiceException, BotTemplateException}
import pme123.ziocamundabot.camunda._
import pme123.ziocamundabot.json.Json
import pme123.ziocamundabot.polling.Polling
import pme123.ziocamundabot.callbackRegister.{CalllbackRegister, RegisterCallback, ResultCallback}
import pme123.ziocamundabot.chatRegister.ChatRegister
import pme123.ziocamundabot.template.Template
import zio._

object bot {

  type BotEnv = Camunda with Json with CalllbackRegister with ChatRegister with Template with Polling

  type Bot = Has[Service]

  trait Service {
    def initBot(): IO[BotException, Unit]

    def sendMessage(chatId: ChatId,
                    maybeRCs: Option[RegisterCallback],
                    msg: String
                   ): IO[BotException, Message]

  }

  def initBot(): ZIO[Bot, BotException, Unit] =
    ZIO.accessM(_.get.initBot())

  def sendMessage(chatId: ChatId, maybeRCs: Option[RegisterCallback], msg: String): ZIO[Bot, BotException, Message] =
    ZIO.accessM(_.get.sendMessage(chatId, maybeRCs, msg))


  def live(bot: TelegramBot): ZLayer[BotEnv, Nothing, Bot] = ZLayer.fromFunction { env: BotEnv =>
    val pollingService = env.get[polling.Service]
    val registerService = env.get[callbackRegister.Service]
    val jsonService = env.get[json.Service]
    val camundaService = env.get[camunda.Service]
    val templateService = env.get[template.Service]


    new Service {

      def initBot(): IO[BotException, Unit] = myBot.unit

      def sendMessage(chatId: ChatId,
                      maybeRCs: Option[RegisterCallback],
                      msg: String
                     ): IO[BotException, Message] = {
        println(s"SendMessage: $chatId")
        myBot.flatMap(_.sendMessage(chatId, maybeRCs, msg))
          .mapError { case e: Throwable =>
            BotServiceException(s"Problem Sending message: ${e.getMessage}", e)
          }
      }

      lazy val myBot: IO[BotException, TelegramBot] =
        for {
          _ <- pollingService.startPolling()
            .mapError(e =>
              BotServiceException("Problem polling", e))
          _ <- listen(bot.queue).fork
        } yield bot

      def listen(queue: Queue[CallbackQuery]): IO[BotException, Unit] =
        for {
          _ <- queue.take
            .flatMap(handleCallback1).forever
            .mapError(q => BotCallbackException(s"Problem take from queue: $q"))
        } yield ()

      def user(botUser: models.User): User =
        User(botUser.id, botUser.firstName, botUser.lastName, botUser.username.getOrElse("--"))

      def handleCallback1(cbq: CallbackQuery,
                         ): IO[BotCallbackException, Unit] =
        (for {
          b <- myBot
          _ <- ZIO.effect(b.request(AnswerCallbackQuery(cbq.id, Some("processing..."))))
          maybeRC <- registerService.requestCallback(cbq.data.getOrElse("---"))
          _ <- handleCallback(cbq, maybeRC)
          _ <- maybeRC.map { regCallback =>
            val botTaskResult = BotTaskResult(regCallback.botTaskIdent, regCallback.callbackId, user(cbq.from))
            for {
              json <- jsonService.toJson(botTaskResult)
              _ <- camundaService.signal(
                Signal(regCallback.signal,
                  Map("botTaskResult" -> Variable(json.toString)))
              )
            } yield ()
          }.getOrElse(ZIO.succeed("ok"))

        } yield ())
          .mapError(f => BotCallbackException(s"Problem handle Callback: $f"))


      def handleCallback(cbq: CallbackQuery,
                         maybeRC: Option[ResultCallback],
                        ): IO[BotException, List[Unit]] =
        ZIO.collectAll {
          (for {
            callbackIdent <- cbq.data
            msg <- cbq.message
          } yield {
            (for {
              text <- maybeRC match {
                case Some(regCallback: ResultCallback) =>
                  templateService.generate(cbq.from.username.get, regCallback.response, regCallback.botTaskIdent)
                    .mapError(e => BotTemplateException("Problem generate Template", e))
                case None =>
                  IO.effectTotal(s"Sorry, this issue (${TelegramBot.extractRequestId(callbackIdent)}) was claimed already!")
              }
              _ <- bot.request(
                SendMessage(
                  msg.source,
                  text))
                  .mapError( e => BotServiceException("Problem send Message.", e))
            } yield ())

          }).toList
        }
    }
  }

  sealed trait BotException extends AppException

  object BotException {

    case class BotServiceException(msg: String, cause: Throwable) extends BotException

    case class BotTemplateException(msg: String, cause: Throwable) extends BotException

    case class BotCallbackException(msg: String) extends BotException

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
