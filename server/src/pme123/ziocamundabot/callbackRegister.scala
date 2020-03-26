package pme123.ziocamundabot

import pme123.ziocamundabot.bot._
import zio.ZLayer.NoDeps
import zio._
import zio.stm.TMap

object callbackRegister {
  type CalllbackRegister = Has[Service]

  trait Service {

    def registerCallback(botTask: BotTask): IO[RegisterCallbackException, Option[RegisterCallback]]

    def requestCallback(callbackIdent: String): UIO[Option[ResultCallback]]

    def myTasks(maybeId: Option[ChatUserOrGroup], chatId: ChatId): UIO[Seq[String]]
  }

  def registerCallback(botTask: BotTask): ZIO[CalllbackRegister, RegisterCallbackException, Option[RegisterCallback]] =
    ZIO.accessM(_.get.registerCallback(botTask))

  def requestCallback(callbackIdent: String): URIO[CalllbackRegister, Option[ResultCallback]] =
    ZIO.accessM(_.get.requestCallback(callbackIdent))

  def myTasks(maybeId: Option[ChatUserOrGroup], chatId: ChatId): URIO[CalllbackRegister, Seq[String]] =
    ZIO.accessM(_.get.myTasks(maybeId, chatId))

  val live: NoDeps[Nothing, CalllbackRegister] = ZLayer.fromEffect {
    TMap.empty[BotTaskIdent, RegisterCallback].commit.map { callbackMap =>
      new Service {

        def registerCallback(botTask: BotTask): IO[RegisterCallbackException, Option[RegisterCallback]] =
          ZIO.fromOption(RegisterCallback(botTask))
            .flatMap(callbackMap.put(botTask.ident, _).commit)
            .as(RegisterCallback(botTask))
            .catchAll(_ =>
              ZIO.fail(RegisterCallbackException("Could not Register callback as RegisterCallback is not set.")))


        def requestCallback(callbackIdent: String): UIO[Option[ResultCallback]] =
          callbackMap.get(RequestCallback(callbackIdent).requestId).commit
            .map { maybeReg =>
              for {
                reg <- maybeReg
                control <- reg.callback.controls.find(_.ident == callbackIdent)
              } yield
                ResultCallback(reg.botTaskIdent, reg.callback.signal, control.ident, control.response)
            }

        def myTasks(maybeId: Option[ChatUserOrGroup], chatId: ChatId): UIO[Seq[String]] =
          for {
            values <- callbackMap.values.commit
          } yield getCallbacks(maybeId, values)

        private def getCallbacks(maybeId: Option[ChatUserOrGroup], v: Iterable[RegisterCallback]) = {
          for {
            id <- maybeId.toSeq
            rc <- v
            if rc.userId == id
          } yield
            rc.botTaskIdent
        }
      }
    }
  }

  case class RegisterCallbackException(msg: String) extends AppException

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

  case class RequestCallback private (callbackIdent: String) {
    val requestId: String = TelegramBot.extractRequestId(callbackIdent)
    val callbackId: String = TelegramBot.extractCallbackId(callbackIdent)
  }

  object RequestCallback {
    def apply(callbackIdent: String): RequestCallback =
      new RequestCallback(callbackIdent) //TODO handle possible exception
  }

  case class ResultCallback(botTaskIdent: String, signal: String, callbackId: String, response: String)

}

