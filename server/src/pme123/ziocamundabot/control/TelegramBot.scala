package pme123.ziocamundabot.control

import cats.MonadError
import com.bot4s.telegram.api.BotBase
import com.bot4s.telegram.clients.SttpClient
import com.softwaremill.sttp.SttpBackend
import zio.Task

class TelegramBot(
                   token: String,
                   backend: SttpBackend[Task, Nothing],
                   telegramHost: String = "api.telegram.org"
                 )(implicit monadError: MonadError[Task, Throwable]) extends BotBase[Task] {

  override val monad: MonadError[Task, Throwable] = monadError

  implicit private val b: SttpBackend[Task, Nothing] = backend
  val client = new SttpClient[Task](token, telegramHost)
}
