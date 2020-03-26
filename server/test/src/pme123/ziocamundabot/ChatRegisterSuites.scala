package pme123.ziocamundabot

import zio.test.Assertion.equalTo
import zio.test.TestAspect._
import zio.test.{testM, _}

object ChatRegisterSuites
  extends DefaultRunnableSpec {

  def spec: ZSpec[environment.TestEnvironment, Any] =
    suite("ChatRegisterSuites")(
      testM("the Chat can be registered") {
        for {
          msg <- chatRegister.registerChat(Some("myChat"), 1212L)
          chatId <- chatRegister.requestChat("myChat")
        } yield
          assert(msg)(equalTo("your chat was successful registered")) &&
            assert(chatId)(equalTo(1212L))
      },
      testM("the Chat can not be registered") {
        for {
          err <- chatRegister.registerChat(None, 1212L).flip
        } yield
          assert(err.msg)(equalTo("Could not Register chat as ChatUserOrGroup is not set."))
      }
    ).provideCustomLayer(chatRegister.live) @@ sequential
}