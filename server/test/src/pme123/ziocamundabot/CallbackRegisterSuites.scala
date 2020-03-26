package pme123.ziocamundabot

import pme123.ziocamundabot.bot.{BotTask, Callback, Control}
import pme123.ziocamundabot.callbackRegister.{RegisterCallback, ResultCallback}
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.{testM, _}

object CallbackRegisterSuites
  extends DefaultRunnableSpec {

  def spec: ZSpec[environment.TestEnvironment, Any] =

    suite("CallbackRegisterSuites")(
      testM("the Callback can be registered") {
        val botTask = BotTask("myBotTask", "myGroup", "just do it", Some(Callback("mySignal", Seq(Control("myBotTask--myCallback", "YES", "my Response")))))
        for {
          maybeCallback <- callbackRegister.registerCallback(botTask)
          maybeCallbackReq <- callbackRegister.requestCallback("myBotTask--myCallback")
        } yield
          assert(maybeCallback)(equalTo(Some(RegisterCallback("myBotTask", "myGroup", Callback("mySignal", Seq(Control("myBotTask--myCallback", "YES", "my Response"))))))) &&
         assert(maybeCallbackReq)(equalTo(Some(ResultCallback("myBotTask", "mySignal", "myBotTask--myCallback", "my Response"))))
      },
      testM("the Callback can not be registered") {
        val botTask = BotTask("myCallback", "myGroup", "my Response", None)
        for {
          err <- callbackRegister.registerCallback(botTask).flip
        } yield
          assert(err.msg)(equalTo("Could not Register callback as RegisterCallback is not set."))
      }
    ).provideCustomLayer(callbackRegister.live) @@ sequential
}