package pme123.ziocamundabot.telegram

import canoe.api._
import canoe.models.{Chat, Group, PrivateChat, Supergroup}
import canoe.syntax._
import play.api.libs.json.JsValue
import pme123.ziocamundabot.camunda.messageHandler.MessageHandler
import pme123.ziocamundabot.camunda.{CamMessage, messageHandler}
import pme123.ziocamundabot.register.callbackRegister.CallbackRegister
import pme123.ziocamundabot.register.{callbackRegister, chatRegister}
import pme123.ziocamundabot.register.chatRegister.ChatRegister
import pme123.ziocamundabot.telegram.canoeClient.CanoeTaskClient
import zio.interop.catz._
import zio.{Has, Task, URLayer, ZLayer}

/**
  * For conversations initialized by the User, you define Scenarios in Canoe.
  * /register - Registers you to the Bot.
  * /mytasks - My pending Tasks.
  * /newtask - Report an Issue.
  */
object canoeScenarios {
  type CanoeScenarios = Has[Service]

  trait Service {
    def newTask: Scenario[Task, Unit]

    def myTasks: Scenario[Task, Unit]

    def register: Scenario[Task, Unit]
  }

  type LiveDeps = Has[CanoeTaskClient] with MessageHandler with ChatRegister with CallbackRegister

  def live: URLayer[LiveDeps, CanoeScenarios] =
    ZLayer.fromServices[CanoeTaskClient, messageHandler.Service, chatRegister.Service, callbackRegister.Service, Service] {
      (canoeClient, messageHandlerService, chatRegisterService, callbackRegisterService) =>

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

          def myTasks: Scenario[Task, Unit] = {
            def handleUserOrGroup(chat: Chat) = {
              chat match {
                case pChat: PrivateChat =>
                  val myTasksTask: Task[Seq[String]] = callbackRegisterService.myTasks(pChat.username, chat.id)
                  for {
                    myTasks <- Scenario.eval(myTasksTask)
                    text = if (myTasks.nonEmpty) myTasks.mkString("\n") else "No open Tasks"
                    _ <- Scenario.eval(chat.send(text))
                  } yield ()
                case _: Group | _: Supergroup =>
                  for {
                    _ <- Scenario.eval(chat.send(s"Hello!\nSorry Groups cannot have Tasks"))
                  } yield ()
              }
            }

            for {
              chat <- Scenario.expect(command("mytasks").chat)
              _ <- handleUserOrGroup(chat)
            } yield ()
          }

          def register: Scenario[Task, Unit] = {
            def handleUserOrGroup(chat: Chat) = {
              chat match {
                case pChat: PrivateChat =>
                  val chatIdTask: Task[String] = chatRegisterService.registerChat(pChat.username, chat.id)
                  for {
                    _ <- Scenario.eval(chatIdTask)
                    _ <- Scenario.eval(chat.send(s"Hello ${(pChat.firstName ++ pChat.lastName).mkString(" ")}, you are successfully registered!"))

                  } yield ()
                case _: Group | _: Supergroup =>
                  for {
                    _ <- Scenario.eval(chat.send(s"Hello!\nSorry you cannot register Groups"))
                  } yield ()
              }
            }

            for {
              chat <- Scenario.expect(command("register").chat)
              _ <- handleUserOrGroup(chat)
            } yield ()
          }
        }
    }
}