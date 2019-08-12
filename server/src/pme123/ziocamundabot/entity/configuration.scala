package pme123.ziocamundabot.entity

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

object configuration {

  case class Config(camunda: CamundaConfig, bot: BotConfig)

  object Config {
    implicit val configReader: ConfigReader[Config] = deriveReader[Config]
  }

  case class CamundaConfig(endpoint: String, port: Int, user: String, password: String)

  object CamundaConfig {
    implicit val configReader: ConfigReader[CamundaConfig] = deriveReader[CamundaConfig]
  }

  case class BotConfig(token: String)

  object BotConfig {
    implicit val configReader: ConfigReader[BotConfig] = deriveReader[BotConfig]
  }

}
