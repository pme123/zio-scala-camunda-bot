package pme123.ziocamundabot.camunda

import pme123.ziocamundabot.register.callbackRegister.CallbackRegister
import pme123.ziocamundabot.telegram._
import pme123.ziocamundabot.register.chatRegister.ChatRegister
import pme123.ziocamundabot.configuration.{CamundaConfig, Configuration}
import pme123.ziocamundabot.json.Json
import pme123.ziocamundabot.register.{callbackRegister, chatRegister}
import pme123.ziocamundabot.sttpBackend.SttpTaskBackend
import pme123.ziocamundabot.telegram.botMessageSender
import pme123.ziocamundabot.telegram.botMessageSender.BotMessageSender
import pme123.ziocamundabot.{AppException, configuration, json}
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.playJson._
import zio._
import zio.console.Console

object taskHandler {

  type TaskHandler = Has[Service]

  trait Service {

    def fetchAndLock: IO[AppException, Seq[ExternalTask]]
  }

  def fetchAndLock: ZIO[TaskHandler, AppException, Seq[ExternalTask]] =
    ZIO.accessM(_.get.fetchAndLock)

  type TaskHandlerDeps = Console
    with Configuration
    with Has[SttpTaskBackend]
    with Json
    with ChatRegister
    with CallbackRegister
    with BotMessageSender

  val live: ZLayer[TaskHandlerDeps, Nothing, TaskHandler] =
    ZLayer.fromServices[
      Console.Service,
      configuration.Service,
      SttpTaskBackend,
      json.Service,
      chatRegister.Service,
      callbackRegister.Service,
      botMessageSender.Service,
      Service] {
      (consoleService, conf, sttpBackend, jsonService,
       chatReg,
       callbackReg,
       telegramClient) =>

        new Service
          with CamundaClient {

          implicit val backend: SttpTaskBackend = sttpBackend
          val camundaConfig: Task[CamundaConfig] = conf.camundaConf

          def console: Console.Service = consoleService

          private val workerId = "camunda-bot-scheduler"
          private val botTaskTag = "botTask"

          val fetchAndLock: IO[AppException, Seq[ExternalTask]] =
            (for {
              tasks <- postWithResult(externalTaskUri,
                FetchAndLock(workerId, List(Topic("pme.telegram.demo", Seq(botTaskTag)))),
                asJson[Seq[ExternalTask]])
              _ <- ZIO.foreach(tasks)(task =>
                handleExternalTask(task)
                  .fold(
                    t => console.putStrLn(s"Could not handle External Task: ${task.id}\n${t.msg}"),
                    _ => console.putStr(s"Successful handled Task ${task.id}")
                  ))
            } yield ()).forever


          private def handleExternalTask(externalTask: ExternalTask): IO[AppException, Unit] =
            for {
              botTask <- jsonService.fromJsonString[BotTask](externalTask.variables(botTaskTag).value)
              chatId <- chatReg.requestChat(botTask.chatUserOrGroup)
              maybeRCs <- callbackReg.registerCallback(botTask)
              _ <- telegramClient.sendMessage(chatId, maybeRCs, botTask.msg)
              _ <- completeTask(CompleteTask(externalTask.id, workerId, Map.empty))
            } yield ()

          private def completeTask(completeTask: CompleteTask): IO[CamundaException, Unit] =
            post(completeTaskUri(completeTask.taskId), completeTask)
        }
    }

}
