package pme123.ziocamundabot

import pme123.ziocamundabot.camunda.{messageHandler, sttpBackend, taskHandler}
import pme123.ziocamundabot.register.{callbackRegister, chatRegister}
import pme123.ziocamundabot.camunda.sttpBackend.SttpTaskBackend
import pme123.ziocamundabot.telegram.canoeClient.CanoeTaskClient
import pme123.ziocamundabot.telegram._
import zio._
import zio.console.Console
import zio.interop.catz._

/**
  * The main App that wires everything and starts the Bot Client and the Camunda Client.
  */
object CamundaBotApp extends zio.App {

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] =
    (for {
      canoe <- canoeClient.makeCanoeClient
      sttpBackend <- sttpBackend.makeSttpBackend
      _ <- makeProgram(canoe, sttpBackend)
    } yield ())
      .fold(
        _ => 1,
        _ => 0
      )


  private def makeProgram(
                           canoeClient: TaskManaged[CanoeTaskClient],
                           sttpBackend: TaskManaged[SttpTaskBackend]
                         ): ZIO[zio.ZEnv, Any, Any] = {

    val configLayer = configuration.live
    val canoeClientLayer = canoeClient.toLayer
    val chatRegisterLayer = chatRegister.live
    val jsonLayer = json.live
    val callbackRegisterLayer = callbackRegister.live
    val templateLayer = template.live
    val messageHandlerLayer = (configLayer ++ Console.live ++ sttpBackend.toLayer) >>> messageHandler.live
    val canoeScenarioLayer = (messageHandlerLayer ++ canoeClientLayer ++ chatRegisterLayer ++ callbackRegisterLayer) >>> canoeScenarios.live
    val botMessageSenderLayer = canoeClientLayer >>> botMessageSender.live
    val callbackHandlerLayer = (botMessageSenderLayer ++ callbackRegisterLayer ++ jsonLayer ++ messageHandlerLayer ++ templateLayer) >>> callbackHandler.live
    val telegramClientLayer = (canoeClientLayer ++ canoeScenarioLayer ++ callbackHandlerLayer) >>> telegramClient.live
    val taskHandlerLayer = (configLayer ++ Console.live ++ sttpBackend.toLayer ++ jsonLayer ++ chatRegisterLayer ++ callbackRegisterLayer ++ messageHandlerLayer ++ botMessageSenderLayer) >>> taskHandler.live

    val botClient = telegramClient.start
      .flatMapError { e =>
        zio.console.putStr(s"ERROR Bot Client: ${e.getClass}:\n ${e.printStackTrace()}")
      }
    val camundaClient = taskHandler.fetchAndLock
      .flatMapError { e =>
        zio.console.putStr(s"ERROR Camunda Client: ${e.getClass}:\n ${e.printStackTrace()}")
      }

    (botClient.fork *> camundaClient)
      .provideCustomLayer(Console.live ++ telegramClientLayer ++ taskHandlerLayer)
  }

}