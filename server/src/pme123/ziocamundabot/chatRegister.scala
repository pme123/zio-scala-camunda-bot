package pme123.ziocamundabot

import pme123.ziocamundabot.bot.{ChatId, ChatUserOrGroup}
import zio.{Has, IO, UIO, ZIO, ZLayer}
import zio.ZLayer.NoDeps
import zio.stm.TMap

object chatRegister {
  type ChatRegister = Has[Service]

  trait Service {

    def registerChat(maybeId: Option[ChatUserOrGroup], chatId: ChatId): IO[RegisterChatException, String]

    def requestChat(chatUserOrGroup: ChatUserOrGroup): UIO[ChatId]

  }

  def registerChat(maybeId: Option[ChatUserOrGroup], chatId: ChatId): ZIO[ChatRegister, RegisterChatException, String] =
    ZIO.accessM(_.get.registerChat(maybeId, chatId))

  def requestChat(chatUserOrGroup: ChatUserOrGroup): ZIO[ChatRegister, Nothing, ChatId] =
    ZIO.accessM(_.get.requestChat(chatUserOrGroup))


  private val camunda_group = "camunda_group"
  private val camundaChatId: ChatId = -319641852L
  private val chatIdMapSTM = TMap.make(camunda_group -> camundaChatId, "pme123" -> 275469757L)

  val live: NoDeps[Nothing, ChatRegister] = ZLayer.fromEffect {
    chatIdMapSTM.commit.map { chatMap =>
      new Service {

        def registerChat(maybeChatUserOrGroup: Option[ChatUserOrGroup], chatId: ChatId): ZIO[Any, RegisterChatException, String] =
          ZIO.fromOption(maybeChatUserOrGroup)
            .flatMap(chatMap.put(_, chatId).commit)
            .as("your chat was successful registered")
            .catchAll(_ =>
              ZIO.fail(RegisterChatException("Could not Register chat as ChatUserOrGroup is not set.")))

        def requestChat(chatUserOrGroup: ChatUserOrGroup): UIO[ChatId] =
          chatMap.getOrElse(chatUserOrGroup, camundaChatId).commit

      }
    }
  }

  case class RegisterChatId(maybeId: Option[ChatUserOrGroup], chatId: ChatId)

  case class RequestChatId(chatUserOrGroup: ChatUserOrGroup)

  case class RegisterChatException(msg: String)  extends AppException

}
