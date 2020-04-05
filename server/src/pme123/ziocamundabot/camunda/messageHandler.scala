package pme123.ziocamundabot.camunda

import play.api.libs.json.JsValue
import pme123.ziocamundabot.configuration
import pme123.ziocamundabot.configuration.Configuration
import sttpBackend.SttpTaskBackend
import sttp.client.playJson._
import zio._
import zio.console.Console

/**
  * Send Messages to Camunda.
  */
object messageHandler {

  type MessageHandler = Has[Service]

  trait Service {

    def sendMessage(message: CamMessage): IO[CamundaException, JsValue]
  }

  def sendMessage(message: CamMessage): ZIO[MessageHandler, CamundaException, JsValue] =
    ZIO.accessM(_.get.sendMessage(message))

  type CamundaDeps = Has[SttpTaskBackend] with Configuration with Console

  val live: ZLayer[CamundaDeps, Nothing, MessageHandler] =
    ZLayer.fromServices[Console.Service, configuration.Service, SttpTaskBackend, Service] { (console, conf, backend) =>
      Live(conf.camundaConf, console)(backend)
    }

  case class Live(camundaConfig: Task[configuration.CamundaConfig],
                  console: Console.Service,
                 )(implicit val backend: SttpTaskBackend)
    extends Service
      with CamundaClient {

    def sendMessage(message: CamMessage): IO[CamundaException, JsValue] =
      postWithResult(messageUri, message, asJson[JsValue])

  }

}
