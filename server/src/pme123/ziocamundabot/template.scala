package pme123.ziocamundabot

import org.fusesource.scalate.TemplateEngine
import zio._

/**
  * generates Message for the Bot Client. E.g: Thanks, {{username}} claimed the issue {{botTaskIdent}}!
  * It uses Scalate TemplateEngine
  */
object template {
  type Template = Has[Service]

  trait Service {
    def generate(username: String, response: String, botTaskIdent: String): Task[String]

    def generate(text: String, templParams: Map[String, String]): Task[String]
  }

  def generate(username: String, response: String, botTaskIdent: String): RIO[Template, String] =
    ZIO.accessM(_.get.generate(username, response, botTaskIdent))

  def generate(text: String, templParams: Map[String, String]): RIO[Template, String] =
    ZIO.accessM(_.get.generate(text, templParams))

  def live: ULayer[Template] = ZLayer.succeed(
    new Service {
      val engine = new TemplateEngine()

      def generate(username: String, response: String, botTaskIdent: String): Task[String] =
        generate(response, TemplateProps(username, botTaskIdent).toMap)

      def generate(text: String, templParams: Map[String, String]): Task[String] = {
        val template = engine.compileMoustache(text)
        ZIO.effect(engine.layout("notused", template, templParams))
      }
    }
  )


  case class TemplateProps(username: String, botTaskIdent: String) {
    def toMap: Map[String, String] =
      Map("username" -> username,
        "botTaskIdent" -> botTaskIdent)
  }

}
