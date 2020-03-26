package pme123.ziocamundabot.boundary

import com.bot4s.telegram.models.CallbackQuery
import com.softwaremill.sttp.asynchttpclient.zio.AsyncHttpClientZioBackend
import pme123.ziocamundabot.bot.{Bot, _}
import pme123.ziocamundabot.camunda.{Camunda, _}
import pme123.ziocamundabot.configuration.CamundaConfig
import pme123.ziocamundabot.json.{Json, _}
import pme123.ziocamundabot.polling.Polling
import pme123.ziocamundabot.callbackRegister.{CalllbackRegister, _}
import pme123.ziocamundabot.chatRegister.ChatRegister
import pme123.ziocamundabot.template.Template
import pme123.ziocamundabot.{bot, _}
import zio.ZLayer.NoDeps
import zio._
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration._
import zio.interop.catz.monadErrorInstance

import scala.io.Source

object CamundaBotRunner extends App {

  //type AppEnvironment = Console with Camunda with Bot with Register with Json with Clock
  type AppEnvironment = Clock with Console with Camunda with Bot with CalllbackRegister with Json

  private def camundaLayer(camundaConfig: CamundaConfig): NoDeps[Nothing, Camunda] = {

    val jsonLayer: NoDeps[Nothing, Json] = json.live
    (jsonLayer ++ Console.live) >>> camunda.live(camundaConfig)

  }

  private def environment(camundaConfig: CamundaConfig, myBot: TelegramBot) = {

    val jsonLayer: NoDeps[Nothing, Json] = json.live
    val pollingLayer: NoDeps[Nothing, Polling] = polling.live(myBot)
    val templateLayer: NoDeps[Nothing, Template] = template.live
    val callbackRegisterLayer: NoDeps[Nothing, CalllbackRegister] = callbackRegister.live
    val chatRegisterLayer: NoDeps[Nothing, ChatRegister] = chatRegister.live
    val camLayer = camundaLayer(camundaConfig)
    val serviceLayer: NoDeps[Nothing, BotEnv] = camLayer ++ jsonLayer ++ chatRegisterLayer ++ callbackRegisterLayer ++ templateLayer ++ pollingLayer
    val botLayer: NoDeps[Nothing, Bot] = serviceLayer >>> bot.live(myBot)

    Console.live ++ Clock.live ++ botLayer ++ jsonLayer ++ callbackRegisterLayer ++ chatRegisterLayer ++ camLayer
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
    (for {
      _ <- putStrLn("Let's start!")
      camundaConf <- configuration.camundaConf.provideCustomLayer(configuration.live)
      token <- readToken
      queue <- Queue.bounded[CallbackQuery](1000)
      myBot = TelegramBot(
        queue,
        (maybeId: Option[ChatUserOrGroup], chatId: ChatId) =>
          Task.effect(Runtime.default.unsafeRun(
            chatRegister.registerChat(maybeId, chatId).unit.provideLayer(chatRegister.live))),
        AsyncHttpClientZioBackend(),
        token
      )
      env = environment(camundaConf, myBot)
      _ <- program.provideLayer(env)
    } yield ())
      .mapError(e => println("ERROR: " + e))
      .fold(_ => 1, _ => 0)
  }

  private lazy val program = {
    for {
      _ <- putStrLn("Program start!")
      _ <- fetchAndProcessTasks
      //  _ <- bot.initBot()
    } yield ()
  }

  private lazy val readToken = Managed.make(Task(Source.fromFile("bot.token")))(source => Task.effect(source.close()).ignore).use {
    source =>
      Task.effect(source.getLines().next())
  }
  private val workerId = "camunda-bot-scheduler"
  private val botTaskTag = "botTask"

  private lazy val fetchAndProcessTasks =
    (for {
      externalTasks <- fetchAndLock(FetchAndLock(workerId, List(Topic("pme.telegram.demo", Seq(botTaskTag)))))
      _ <- console.putStrLn("FETCHED TASKS " + externalTasks)
      _ <- ZIO.foreach(externalTasks)(task => handleExternalTask(task))
    } yield ())
      .repeat(Schedule.spaced(1.second)).forever

  private def handleExternalTask(externalTask: ExternalTask) =
    for {
      botTask <- fromJsonString[BotTask](externalTask.variables(botTaskTag).value)
      chatId <- chatRegister.requestChat(botTask.chatUserOrGroup)
      _ <- putStrLn(s"chatId: $chatId")
      maybeRCs <- registerCallback(botTask)
      _ <- putStrLn(s"registerCallback")
      _ <- sendMessage(chatId, maybeRCs, botTask.msg)
      _ <- putStrLn(s"message sent")
      _ <- completeTask(CompleteTask(externalTask.id, workerId, Map.empty))
      _ <- putStrLn(s"complete task")
    } yield ()
}


object MyApp extends App {
  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
    UIO(println("oki")).repeat(Schedule.spaced(1.second)).forever
  }
}
