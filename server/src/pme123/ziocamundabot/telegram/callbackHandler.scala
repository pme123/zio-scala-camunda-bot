package pme123.ziocamundabot.telegram

import canoe.models.{CallbackButtonSelected, CallbackQuery, Update, User => CanoeUser}
import cats.Applicative
import fs2.Pipe
import BotException._
import pme123.ziocamundabot.register.callbackRegister.{CallbackRegister, ResultCallback}
import pme123.ziocamundabot.camunda.messageHandler.MessageHandler
import pme123.ziocamundabot.camunda.{CamMessage, Variable, messageHandler}
import pme123.ziocamundabot.json.Json
import pme123.ziocamundabot.register.callbackRegister
import pme123.ziocamundabot.telegram.botMessageSender.BotMessageSender
import pme123.ziocamundabot.template.Template
import pme123.ziocamundabot.{json, template}
import zio._
import zio.interop.catz._

object callbackHandler {
  type CallbackHandler = Has[Service]

  trait Service {
    def answerCallbacks: Pipe[Task, Update, Update]
  }

  type CallbackHandlerDeps = BotMessageSender with CallbackRegister with Json with MessageHandler with Template

  def live: URLayer[CallbackHandlerDeps, CallbackHandler] =
    ZLayer.fromServices[botMessageSender.Service, callbackRegister.Service, json.Service, messageHandler.Service, template.Service, Service] {
      (botMessageSender, callbackRegisterService, jsonService, messageHandler, templateService) =>
        new Service {

          def answerCallbacks: Pipe[Task, Update, Update] =
            _.evalTap {
              case CallbackButtonSelected(_, query) =>
                query.data match {
                  case Some(dataStr) if dataStr.startsWith(CALLBACK_TAG) =>
                    handleCallback1(dataStr.substring(CALLBACK_TAG.length), query)
                  case _ => Applicative[Task].unit
                }
              case _ => Applicative[Task].unit
            }

          def handleCallback1(callbackIdent: String, cbq: CallbackQuery,
                             ): Task[Unit] =
            (for {
              //  _ <- botMessageSender.sendMessage(AnswerCallbackQuery(cbq.id, Some("processing...")))
              maybeRC <- callbackRegisterService.requestCallback(callbackIdent)
              _ <- handleCallback(callbackIdent, cbq, maybeRC)
              _ <- maybeRC.map { regCallback =>
                val botTaskResult = BotTaskResult(regCallback.botTaskIdent, regCallback.callbackId, user(cbq.from))
                for {
                  json <- jsonService.toJson(botTaskResult)
                  _ <- messageHandler.sendMessage(
                    CamMessage(regCallback.messageName,
                      regCallback.businessKey,
                      Map("botTaskResult" -> Variable(json.toString)))
                  )
                } yield ()
              }.getOrElse(ZIO.succeed("ok"))

            } yield ())
              .mapError(f => BotCallbackException(s"Problem handle Callback: $f"))

          def handleCallback(callbackIdent: String,
                             cbq: CallbackQuery,
                             maybeRC: Option[ResultCallback],
                            ): IO[BotException, List[Unit]] =
            ZIO.collectAll {
              cbq.message.map { msg =>
                (for {
                  text <- maybeRC match {
                    case Some(regCallback: ResultCallback) =>
                      templateService.generate(cbq.from.username.get, regCallback.response, regCallback.botTaskIdent)
                        .mapError(e => BotTemplateException("Problem generate Template", Some(e)))
                    case None =>
                      IO.effectTotal(s"Sorry, this issue (${extractRequestId(callbackIdent)}) was claimed already!")
                  }
                  _ <- botMessageSender.sendMessage(
                    msg.chat.id,
                    None,
                    text)
                    .mapError(e => BotServiceException("Problem send Message.", Some(e)))
                } yield ())

              }.toList
            }

          def user(botUser: CanoeUser): User =
            User(botUser.id, botUser.firstName, botUser.lastName, botUser.username.getOrElse("--"))


        }

    }

}
