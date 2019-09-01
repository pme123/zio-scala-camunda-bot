package pme123.ziocamundabot.control

import com.bot4s.telegram.api.BotBase
import com.bot4s.telegram.cats.PollingState
import com.bot4s.telegram.methods.{DeleteWebhook, GetMe, GetUpdates}
import com.bot4s.telegram.models.Update
import slogging.StrictLogging
import zio.{Task, ZIO}

import scala.concurrent.duration._

trait Polling extends Serializable with BotBase[Task] with StrictLogging {

  def startPolling(): Task[Unit] =
    request(DeleteWebhook).flatMap(_ =>
      for {
        getMe <- request(GetMe)
        _ <- poll(PollingState(getMe, None))
      } yield ()
    ).mapError(
      _ => new Exception("Can not remove webhook")
    )

  private def poll(state: PollingState): Task[Unit] =
    {
      println("POLL")

      for {
        updates <- pollingGetUpdates(state.offset.map(_ + 1))
        _ <- ZIO.collectAll(updates.toList.map { update =>
          monad.handleErrorWith(receiveUpdate(update, Some(state.botUser))) { e =>
            logger.warn(s"Can not process updates $update", e)
            unit
          }
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
  def pollingGetUpdates(offset: Option[Long]): Task[Seq[Update]] =
    {
      println("GETUPDATES")
      request(
        GetUpdates(
          offset,
          timeout = Some(pollingTimeout.toSeconds.toInt),
          allowedUpdates = allowedUpdates
        )
      )
    }
}

sealed trait PollingException

object PollingException {

  case class PollingReadException(msg: String) extends PollingException

  case class PollingWriteException(msg: String) extends PollingException

}
