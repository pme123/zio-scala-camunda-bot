package pme123.ziocamundabot.boundary

import pme123.ziocamundabot.control.{Bot, Camunda, Json, Register}
import pme123.ziocamundabot.entity.configuration.CamundaConfig
import zio.Runtime
import zio.clock.Clock
import zio.console.Console
import zio.internal.Platform

object AppRuntime
  extends Runtime[Json with Register with Bot with Camunda with Console with Clock] {

  type AppEnvironment = Json with Register with Bot with Camunda with Console with Clock
  val Environment: Json with Register with Bot with Camunda with Console with Clock = _

  def environment(camundaConfig: CamundaConfig, botToken: String): AppEnvironment =

    new Json.Live
      with Register.Live
      with Console.Live
      with Clock.Live
      with Bot.Live
      with Camunda.Live {
      val config: CamundaConfig = camundaConfig
      val token: String = botToken
    }

  val Platform: Platform = _
}
