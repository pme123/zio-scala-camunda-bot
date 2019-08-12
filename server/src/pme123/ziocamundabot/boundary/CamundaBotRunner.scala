package pme123.ziocamundabot.boundary

import java.io.File

import cats.effect.ContextShift
import pme123.ziocamundabot.control.Camunda.CamundaEnv
import pme123.ziocamundabot.control.CamundaException.HandleTaskException
import pme123.ziocamundabot.control._
import pme123.ziocamundabot.control.bot._
import pme123.ziocamundabot.control.camunda._
import pme123.ziocamundabot.control.json._
import pme123.ziocamundabot.control.register._
import pme123.ziocamundabot.entity.bot.{BotTask, Receipt}
import pme123.ziocamundabot.entity.camunda.{CompleteTask, ExternalTask, FetchAndLock, Topic}
import pme123.ziocamundabot.entity.configuration.CamundaConfig
import zio._
import zio.clock.Clock
import zio.console.{putStrLn, _}
import zio.duration._

import scala.concurrent.ExecutionContext
import scala.io.Source

object CamundaBotRunner extends App {

   //type MyEnvironment = Json with Register with Bot with Camunda with Console with Clock

  implicit val cs: ContextShift[cats.effect.IO] = cats.effect.IO.contextShift(ExecutionContext.Implicits.global)

  private def environment(camundaConfig: CamundaConfig, botToken: String) =

    new Json.Live
      with Register.Live
      with Console.Live
      with Clock.Live
      with Bot.Live
      with Camunda.Live {
      val config: CamundaConfig = camundaConfig
      val token: String = botToken
    }

  def run(args: List[String]): ZIO[Environment, Nothing, Int] =

    (for {
      _ <- putStrLn("Let's start!")
      conf <- configuration.load.provide(Configuration.Live)
      token <- readToken
      camundaService = ZIO.runtime[CamundaEnv].flatMap { implicit rts =>
        fetchAndProcessTasks.repeat(Schedule.spaced(1.second))
      }
      program <- camundaService.provideSome[Environment]({ _ =>
        environment(conf.camunda, token)
      })
    } yield program)
      .flatMapError(e => putStrLn("ERROR: " + e))
      .fold(_ => 1, _ => 0)

  private lazy val readToken = Managed.make(Task(Source.fromFile("bot.token")))(source => Task.effect(source.close()).ignore).use {
    source =>
      Task.effect(source.getLines().next())
  }
  private val workerId = "camunda-bot-scheduler"
  private val botTaskTag = "botTask"

  private lazy val fetchAndProcessTasks: ZIO[CamundaEnv with Bot, CamundaException, Receipt] =
    for {
      externalTasks <- fetchAndLock(FetchAndLock(workerId, List(Topic("pme.telegram.demo", Seq(botTaskTag)))))
      receipts <- ZIO.foreachParN(5)(externalTasks)(task =>
        handleExternalTask(task)
          .fold(
            t => Receipt.failure(task.id, t),
            _ => Receipt.success(task.id)
          ))
    } yield receipts.foldLeft(Receipt.empty)(_ |+| _)

  private def handleExternalTask(externalTask: ExternalTask): ZIO[CamundaEnv with Bot, Object, Unit] =
    (for {
      botTask <- fromJsonString[BotTask](externalTask.variables(botTaskTag).value)
      chatId <- requestChat(botTask.chatUserOrGroup)
      maybeRCs <- registerCallback(botTask)
      _ <- sendMessage(chatId, maybeRCs, botTask.msg)
      _ <- putStrLn(s"message sent")
      _ <- completeTask(CompleteTask(externalTask.id, workerId, Map.empty))
      _ <- putStrLn(s"complete task")
    } yield ())
      .catchSome {
        case e if !e.isInstanceOf[CamundaException] => ZIO.fail(HandleTaskException(e.toString))
      }


}

