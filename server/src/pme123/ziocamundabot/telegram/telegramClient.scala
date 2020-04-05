package pme123.ziocamundabot.telegram

import canoe.api.Bot
import BotException._
import pme123.ziocamundabot.telegram.callbackHandler.CallbackHandler
import pme123.ziocamundabot.telegram.canoeClient.CanoeTaskClient
import pme123.ziocamundabot.telegram.canoeScenarios.CanoeScenarios
import zio._
import zio.interop.catz._

/**
  * The Telegram Client, sets up the communication with Telegram API
  */
object telegramClient {
  type TelegramClient = Has[Service]

  trait Service {
    def start: IO[BotClientException, Unit]
  }

  type TelegramClientDeps = Has[CanoeTaskClient] with CanoeScenarios with CallbackHandler

  def start: ZIO[TelegramClient, BotClientException, Unit] =
    ZIO.accessM(_.get.start)

  def live: URLayer[TelegramClientDeps, TelegramClient] =
    ZLayer.fromServices[CanoeTaskClient, canoeScenarios.Service, callbackHandler.Service, Service] {
      (canoeClient, scenarios, callbackHandler) =>
        new Service {

          implicit val canoe: CanoeTaskClient = canoeClient


          override def start: IO[BotClientException, Unit] =
            Bot
              .polling[Task]
              .follow(
                scenarios.newTask,
                scenarios.myTasks,
                scenarios.register
              )
              .through(
                callbackHandler.answerCallbacks
              )
              .compile
              .drain
              .mapError(t =>
                BotClientException(s"Problem running the BotClient.", Some(t))
              )
        }
    }
}


