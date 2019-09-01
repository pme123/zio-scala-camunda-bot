package pme123.ziocamundabot.boundary

import pme123.ziocamundabot.control.Camunda.CamundaEnv
import pme123.ziocamundabot.control._
import pme123.ziocamundabot.control.bot._
import pme123.ziocamundabot.control.camunda._
import pme123.ziocamundabot.control.json._
import pme123.ziocamundabot.control.register._
import pme123.ziocamundabot.entity.bot.BotTask
import pme123.ziocamundabot.entity.camunda.{CompleteTask, ExternalTask, FetchAndLock, Topic}
import pme123.ziocamundabot.entity.configuration.CamundaConfig
import zio._
import zio.clock.Clock
import zio.console.{putStrLn, _}
import zio.duration._

import scala.io.Source

object CamundaBotRunner extends App {

  type AppEnvironment = Json with Register with Bot with Camunda with Console with Clock

  private def environment(camundaConfig: CamundaConfig, botToken: String): AppEnvironment =

    new Json.Live
      with Register.Live
      with Console.Live
      with Clock.Live
      with Bot.Live
      with Camunda.Live {
      val config: CamundaConfig = camundaConfig
      val token: String = botToken
    }

  def run(args: List[String]): ZIO[Environment, Nothing, Int] = {
    val program: ZIO[Environment, Nothing, Int] = (for {
      _ <- putStrLn("Let's start!")
      conf <- configuration.load.provide(Configuration.Live)
      token <- readToken
      /*  botService =
        ZIO.runtime[AppEnvironment].flatMap { implicit rts =>
          initBot().forever
        } */
      camundaService =
      ZIO.runtime[AppEnvironment].flatMap { implicit rts =>
        fetchAndProcessTasks
      }
      // _ <- fetchAndProcessTasks.repeat(Schedule.spaced(1.second))
      program <- camundaService.provideSome[Environment]({ _ =>
        //  program <- (camundaService).provideSome[Environment]({ _ =>
        environment(conf.camunda, token)
      })
    } yield program)
      .mapError(e => println("ERROR: " + e))
      .fold(_ => 1, _ => 0)

    program.foldM(
      err => putStrLn(s"Execution failed with: $err") *> IO.succeed(1),
      _ => IO.succeed(0)
    )
  }

  private lazy val readToken = Managed.make(Task(Source.fromFile("bot.token")))(source => Task.effect(source.close()).ignore).use {
    source =>
      Task.effect(source.getLines().next())
  }
  private val workerId = "camunda-bot-scheduler"
  private val botTaskTag = "botTask"

  private lazy val fetchAndProcessTasks: ZIO[AppEnvironment, Object, Int] =
    (for {
      externalTasks <- fetchAndLock(FetchAndLock(workerId, List(Topic("pme.telegram.demo", Seq(botTaskTag)))))
      _ = println("FETCHED TASKS " + externalTasks)
      _ <- ZIO.foreachParN(5)(externalTasks)(task => handleExternalTask(task))
    } yield ())
      .repeat(Schedule.spaced(1.second)).forever

  private def handleExternalTask(externalTask: ExternalTask): ZIO[CamundaEnv with Bot, Object, Unit] =
    (for {
      botTask <- fromJsonString[BotTask](externalTask.variables(botTaskTag).value)
      chatId <- requestChat(botTask.chatUserOrGroup)
      _ <- putStrLn(s"chatId")
      maybeRCs <- registerCallback(botTask)
      _ <- putStrLn(s"registerCallback")
      _ <- sendMessage(chatId, maybeRCs, botTask.msg)
      _ <- putStrLn(s"message sent")
      _ <- completeTask(CompleteTask(externalTask.id, workerId, Map.empty))
      _ <- putStrLn(s"complete task")
    } yield ()).mapError(e => {
      println("yyyyyy: " + e)
      e
    })


  /*
    object Main extends App {

      type AppEnvironment = Clock with Persistence

      type AppTask[A] = TaskR[AppEnvironment, A]

      override def run(args: List[String]): ZIO[Environment, Nothing, Int] = {
        val program: ZIO[Main.Environment, Throwable, Unit] = for {
          conf        <- configuration.load.provide(Configuration.Live)
          blockingEC  <- blocking.blockingExecutor.map(_.asEC).provide(Blocking.Live)

          transactorR = Persistence.mkTransactor(
            conf.dbConfig,
            Platform.executor.asEC,
            blockingEC
          )

          httpApp = Router[AppTask](
            "/users" -> Api(s"${conf.api.endpoint}/users").route
          ).orNotFound

          server = ZIO.runtime[AppEnvironment].flatMap { implicit rts =>
            db.createTable *>
              BlazeServerBuilder[AppTask]
                .bindHttp(conf.api.port, "0.0.0.0")
                .withHttpApp(CORS(httpApp))
                .serve
                .compile[AppTask, AppTask, ExitCode]
                .drain
          }
          program <- transactorR.use { transactor =>
            server.provideSome[Environment] { _ =>
              new Clock.Live with Persistence.Live {
                override protected def tnx: doobie.Transactor[Task] = transactor
              }
            }
          }
        } yield program

        program.foldM(
          err => putStrLn(s"Execution failed with: $err") *> IO.succeed(1),
          _ => IO.succeed(0)
        )
      }
    }
  */
}


object MyApp extends App {
  def run(args: List[String]): ZIO[MyApp.Environment, Nothing, Int] = {
    UIO(println("oki")).repeat(Schedule.spaced(1.second)).forever
  }
}
