package pme123.ziocamundabot

import pme123.ziocamundabot.bot._
import pme123.ziocamundabot.register.RegisterException.RegisterChatException
import zio.ZLayer.NoDeps
import zio.stm.TRef
import zio._

object register {
  type Register = Has[Service]

  type ChatIds = TRef[Map[ChatUserOrGroup, ChatId]]
  type CallbackIdMap = TRef[Map[BotTaskIdent, RegisterCallback]]

  trait Service {

    def registerChat(maybeId: Option[ChatUserOrGroup], chatId: ChatId): IO[RegisterException, String]

    def requestChat(chatUserOrGroup: ChatUserOrGroup): UIO[ChatId]

    def registerCallback(botTask: BotTask): UIO[Option[RegisterCallback]]

    def requestCallback(callbackIdent: String): UIO[Option[ResultCallback]]

    def myTasks(maybeId: Option[ChatUserOrGroup], chatId: ChatId): UIO[Seq[String]]

  }

  def registerChat(maybeId: Option[ChatUserOrGroup], chatId: ChatId): ZIO[Register, RegisterException, String] =
    ZIO.accessM(_.get.registerChat(maybeId, chatId))

  def requestChat(chatUserOrGroup: ChatUserOrGroup): ZIO[Register, Nothing, ChatId] =
    ZIO.accessM(_.get.requestChat(chatUserOrGroup))

  def registerCallback(botTask: BotTask): ZIO[Register, Nothing, Option[RegisterCallback]] =
    ZIO.accessM(_.get.registerCallback(botTask))

  def requestCallback(callbackIdent: String): ZIO[Register, Nothing, Option[ResultCallback]] =
    ZIO.accessM(_.get.requestCallback(callbackIdent))

  def myTasks(maybeId: Option[ChatUserOrGroup], chatId: ChatId): ZIO[Register, Nothing, Seq[String]] =
    ZIO.accessM(_.get.myTasks(maybeId, chatId))

  val live: NoDeps[Nothing, Register] = ZLayer.succeed(new Service {
    private val camunda_group = "camunda_group"

    val chatIdMap: UIO[ChatIds] = TRef.make(Map(camunda_group -> -319641852L, "pme123" -> 275469757L)).commit
    val callbackIdMap: UIO[CallbackIdMap] = TRef.make(Map.empty[String, RegisterCallback]).commit

    def registerChat(maybeId: Option[ChatUserOrGroup], chatId: ChatId): ZIO[Any, RegisterException, String] =
      (for {
        id <- ZIO.fromOption(maybeId)
        tRef <- chatIdMap
        _ <- tRef.update(_ + (id -> chatId)).commit
      } yield "you were successful registered")
        .orElseFail(RegisterChatException("Sorry, you need a Username to talk with me"))

    def myTasks(maybeId: Option[ChatUserOrGroup], chatId: ChatId): UIO[Seq[String]] =
      for {
        tRef <- callbackIdMap
        tasks <- tRef.get.map(m => getCallbacks(maybeId, m.values)).commit
      } yield tasks

    def requestChat(chatUserOrGroup: ChatUserOrGroup): UIO[ChatId] =
      for {
        tRef <- chatIdMap
        tasks <- tRef.get.map(m => m.getOrElse(chatUserOrGroup, m(camunda_group))).commit
      } yield tasks

    def registerCallback(botTask: BotTask): UIO[Option[RegisterCallback]] =
      (for {
        rc <- ZIO.fromOption(RegisterCallback(botTask))
        tRef <- callbackIdMap
        _ <- tRef.update(_ + (botTask.ident -> rc)).commit
      } yield rc)
        .fold(_ => None, Some(_))

    def requestCallback(callbackIdent: String): UIO[Option[ResultCallback]] =
      (for {
        tRef <- callbackIdMap
        requestId = RequestCallback(callbackIdent).requestId
        reg <- tRef.get.map(m => m(requestId)).commit
        _ <- tRef.update(_.filterNot(_._1 == requestId)).commit
        control <- ZIO.fromOption(reg.callback.controls.find(_.ident == callbackIdent))
      } yield
        ResultCallback(reg.botTaskIdent, reg.callback.signal, control.ident, control.response))
        .fold(_ => None, Some(_))


    private def getCallbacks(maybeId: Option[ChatUserOrGroup], v: Iterable[RegisterCallback]) = {
      for {
        id <- maybeId.toSeq
        rc <- v
        if rc.userId == id
      } yield
        rc.botTaskIdent
    }
  }
  )

  sealed trait RegisterException extends Throwable

  object RegisterException {

    case class RegisterChatException(msg: String) extends RegisterException

  }

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
    val requestId: String = TelegramBot.extractRequestId(callbackIdent)
    val callbackId: String = TelegramBot.extractCallbackId(callbackIdent)
  }

  case class ResultCallback(botTaskIdent: String, signal: String, callbackId: String, response: String)

}
