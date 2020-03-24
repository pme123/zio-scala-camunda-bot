package pme123.ziocamundabot

import pureconfig.generic.semiauto.deriveReader
import pureconfig.{ConfigReader, ConfigSource}
import zio.ZLayer.NoDeps
import zio._

object configuration {
  type Configuration = Has[Service]

  trait Service {
    def camundaConf: Task[CamundaConfig]

    def botConf: Task[BotConfig]
  }

  val camundaConf: RIO[Configuration, CamundaConfig] =
    ZIO.accessM(_.get.camundaConf)

  val botConf: RIO[Configuration, BotConfig] =
    ZIO.accessM(_.get.botConf)

  lazy val live: NoDeps[Nothing, Has[Service]] = ZLayer.succeed(
    new Service {
      lazy val camundaConf: Task[CamundaConfig] = conf.map(_.camunda)
      lazy val botConf: Task[BotConfig] = conf.map(_.bot)
      private lazy val conf: Task[Config] = Task.effect(ConfigSource.default.loadOrThrow[Config])
    }
  )

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
