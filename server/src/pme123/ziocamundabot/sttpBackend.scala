package pme123.ziocamundabot

import sttp.client.SttpBackend
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.{Managed, Task, UIO, ZIO}

object sttpBackend {
  type SttpTaskBackend = SttpBackend[Task, Nothing, WebSocketHandler]

  def makeSttpBackend: UIO[Managed[Throwable, SttpBackend[Task, Nothing, WebSocketHandler]]] =
    ZIO
      .runtime[Any]
      .map { implicit rts =>
        Managed.fromEffect(
          AsyncHttpClientZioBackend()
        )
      }
}
