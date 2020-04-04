package pme123.ziocamundabot.telegram

import canoe.api._
import canoe.syntax._
import play.api.libs.json.JsValue
import pme123.ziocamundabot.camunda.messageHandler.MessageHandler
import pme123.ziocamundabot.camunda.{CamMessage, messageHandler}
import pme123.ziocamundabot.telegram.canoeClient.CanoeTaskClient
import zio.interop.catz._
import zio.{Has, Task, URLayer, ZLayer}

object canoeScenarios {
  type CanoeScenarios = Has[Service]

  trait Service {
    def newTask: Scenario[Task, Unit]
  }

  type LiveDeps = Has[CanoeTaskClient] with MessageHandler

  def live: URLayer[LiveDeps, CanoeScenarios] =
    ZLayer.fromServices[CanoeTaskClient, messageHandler.Service, Service] {
      (canoeClient, messageHandlerService) =>

        new Service {

          private implicit val client: CanoeTaskClient = canoeClient

          def newTask: Scenario[Task, Unit] =
            for {
              chat <- Scenario.expect(command("newtask").chat)
              _ <- Scenario.eval(chat.send("Hello. What's the Issue?"))
              task <- Scenario.expect(text)
              message = CamMessage.issue(task)
              value: Task[JsValue] = messageHandlerService.sendMessage(message)
              _ <- Scenario.eval(value)
              _ <- Scenario.eval(chat.send(s"Ok I created the task. Task ID: ${message.businessKey}"))
            } yield ()

        }
    }
}