package pme123.ziocamundabot.control

import pme123.ziocamundabot.control.RegisterException.RegisterChatException
import pme123.ziocamundabot.entity.bot.{BotTask, BotTaskIdent, ChatId, ChatUserOrGroup}
import pme123.ziocamundabot.entity.register.{RegisterCallback, RequestCallback, ResultCallback}
import zio.stm.TRef
import zio.{UIO, ZIO}

trait Register extends Serializable {
  def registry: Register.Service[Any]
}

object Register {

  type ChatIds = TRef[Map[ChatUserOrGroup, ChatId]]
  type CallbackIdMap = TRef[Map[BotTaskIdent, RegisterCallback]]

  trait Service[R] {

    def registerChat(maybeId: Option[ChatUserOrGroup], chatId: ChatId): ZIO[R, RegisterException, String]

    def requestChat(chatUserOrGroup: ChatUserOrGroup): ZIO[R, Nothing, ChatId]

    def registerCallback(botTask: BotTask): ZIO[R, Nothing, Option[RegisterCallback]]

    def requestCallback(callbackIdent: String): ZIO[R, Nothing, Option[ResultCallback]]

    def myTasks(maybeId: Option[ChatUserOrGroup], chatId: ChatId): ZIO[R, Nothing, Seq[String]]

  }

  trait Live extends Register {

    val registry: Service[Any] = new Service[Any] {
      private val camunda_group = "camunda_group"

      val chatIdMap: UIO[ChatIds] = TRef.make(Map(camunda_group -> -319641852L, "pme123" -> 275469757L)).commit
      val callbackIdMap: UIO[CallbackIdMap] = TRef.make(Map.empty[String, RegisterCallback]).commit

      def registerChat(maybeId: Option[ChatUserOrGroup], chatId: ChatId): ZIO[Any, RegisterException, String] =
        (for {
          id <- ZIO.fromOption(maybeId)
          tRef <- chatIdMap
          r <- tRef.update(_ + (id -> chatId)).commit
            .map(_ => "you were successful registered")
        } yield r)
          .mapError(_ => RegisterChatException("Sorry, you need a Username to talk with me"))

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
          r <- tRef.update(_ + (botTask.ident -> rc)).commit
            .map(_ => rc)
        } yield r)
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


    }

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

sealed trait RegisterException extends Throwable

object RegisterException {

  case class RegisterChatException(msg: String) extends RegisterException

}
