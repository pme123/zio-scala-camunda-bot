package pme123.ziocamundabot

import com.bot4s.telegram.models.Message
import play.api.libs.{json => j}
import pme123.ziocamundabot.entity.bot.{BotTask, ChatId, ChatUserOrGroup}
import pme123.ziocamundabot.entity.camunda.{CompleteTask, ExternalTask, FetchAndLock, Signal}
import pme123.ziocamundabot.entity.configuration.Config
import pme123.ziocamundabot.entity.register.{RegisterCallback, ResultCallback}
import zio.{TaskR, ZIO}

package object control {

  object configuration extends Configuration.Service[Configuration] {
    val load: TaskR[Configuration, Config] =
      ZIO.accessM(_.config.load)
  }

  object camunda extends Camunda.Service[Camunda] {

    def fetchAndLock(fetchAndLock: FetchAndLock): ZIO[Camunda, CamundaException, Seq[ExternalTask]] =
      ZIO.accessM(_.camunda fetchAndLock fetchAndLock)

    def completeTask(completeTask: CompleteTask): ZIO[Camunda, CamundaException, j.JsValue] =
      ZIO.accessM(_.camunda completeTask completeTask)

    def signal(signal: Signal): ZIO[Camunda, CamundaException, j.JsValue] =
      ZIO.accessM(_.camunda signal signal)
  }

  object json extends Json.Service[Json] {
    def fromJsonString[T](jsonStr: String)(implicit reader: j.Reads[T]): ZIO[Json, JsonException, T] =
      ZIO.accessM(_.json fromJsonString jsonStr)

    def toJson[T](obj: T)(implicit reader: j.Writes[T]): ZIO[Json, Nothing, j.JsValue] =
      ZIO.accessM(_.json toJson obj)

  }

  object register extends Register.Service[Register] {

    def registerChat(maybeId: Option[ChatUserOrGroup], chatId: ChatId): ZIO[Register, String, String] =
      ZIO.accessM(_.registry registerChat(maybeId, chatId))

    def requestChat(chatUserOrGroup: ChatUserOrGroup): ZIO[Register, Nothing, ChatId] =
      ZIO.accessM(_.registry requestChat chatUserOrGroup)

    def registerCallback(botTask: BotTask): ZIO[Register, Nothing, Option[RegisterCallback]] =
      ZIO.accessM(_.registry registerCallback botTask)

    def requestCallback(callbackIdent: String): ZIO[Register, Nothing, Option[ResultCallback]] =
      ZIO.accessM(_.registry requestCallback callbackIdent)

    def myTasks(maybeId: Option[ChatUserOrGroup], chatId: ChatId): ZIO[Register, Nothing, Seq[String]] =
      ZIO.accessM(_.registry myTasks(maybeId, chatId))
  }

  object bot extends Bot.Service[Bot] {
    def sendMessage(chatId: ChatId, maybeRCs: Option[RegisterCallback], msg: String): ZIO[Bot, BotException, Message] =
      ZIO.accessM(_.bot sendMessage(chatId, maybeRCs, msg))
  }

}
