package pme123.ziocamundabot

import com.bot4s.telegram.api.BotBase
import com.bot4s.telegram.cats.PollingState
import com.bot4s.telegram.methods.{DeleteWebhook, GetMe, GetUpdates}
import com.bot4s.telegram.models.Update
import zio.ZLayer.NoDeps
import zio._

import scala.concurrent.duration.{Duration, _}

object polling {
  type Polling = Has[Service]

  trait Service {
    def startPolling(): Task[Unit]
  }

  def startPolling(): RIO[Polling, Unit] =
    ZIO.accessM(_.get.startPolling())

  def live(botBase: BotBase[Task]): NoDeps[Nothing, Polling] = ZLayer.succeed(
    new Service {
      import botBase._
      def startPolling(): Task[Unit] =
        botBase.request(DeleteWebhook).flatMap(_ =>
          for {
            getMe <- botBase.request(GetMe)
            _ <- poll(PollingState(getMe, None)).fork
          } yield ()
        ).catchAll( ex =>
          ZIO.fail(new Exception("Can not remove webhook"))
        )

      private def poll(state: PollingState): Task[Unit] = {
        for {
          updates <- pollingGetUpdates(state.offset.map(_ + 1))
          _ <- ZIO.collectAll(updates.toList.map { update =>
            botBase.receiveUpdate(update, Some(state.botUser))
          })
          nextOffset = updates
            .foldLeft(state.offset) { case (acc, u) => Some(acc.fold(u.updateId)(u.updateId max _)) }
          _ <- poll(state.copy(offset = nextOffset))
        } yield ()
      }

      /**
        * Long-polling timeout in seconds.
        *
        * Specifies the amount of time the connection will be idle/waiting if there are no updates.
        */
      def pollingTimeout: Duration = 30.seconds

      /**
        * The polling method, overload for custom behavior, e.g. back-off, retries...
        *
        */
      def pollingGetUpdates(offset: Option[Long]): Task[Seq[Update]] = {
        println("GETUPDATES")
        botBase.request(
          GetUpdates(
            offset,
            timeout = Some(pollingTimeout.toSeconds.toInt),
            allowedUpdates = allowedUpdates
          )
        )
      }
    }
  )


  sealed trait PollingException

  object PollingException {

    case class PollingReadException(msg: String) extends PollingException

    case class PollingWriteException(msg: String) extends PollingException

  }

}
