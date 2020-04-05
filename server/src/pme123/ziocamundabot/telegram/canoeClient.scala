package pme123.ziocamundabot.telegram

import canoe.api.TelegramClient
import zio.interop.catz._
import zio.{Managed, Task, TaskManaged, ZIO}

import scala.io.Source

/**
  * For the implementation we need a Canoe TelegramClient.
  */
object canoeClient {
  type CanoeTaskClient = TelegramClient[Task]

  lazy val makeCanoeClient: Task[TaskManaged[CanoeTaskClient]] =
    ZIO
      .runtime[Any]
      .flatMap { implicit rts =>
        readToken.map(t =>
          TelegramClient
          .global[Task](t)
          .toManaged
        )
      }

  private lazy val readToken: ZIO[Any, Throwable, String] =
    Managed.make(Task(Source.fromFile("bot.token")))(source =>
      Task.effect(source.close()).ignore).use {
    source =>
      Task.effect(source.getLines().next())
  }
}
