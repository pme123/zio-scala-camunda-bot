package pme123.ziocamundabot.control

import org.fusesource.scalate.TemplateEngine

//TODO ZIOnize
object TextTemplateEngine {

  val engine = new TemplateEngine()

  def generate(username: String, response: String, botTaskIdent: String): String =
    generate(response, TemplateProps(username, botTaskIdent).toMap)

  def generate(text: String, templParams: Map[String, String]): String = {
    val template = engine.compileMoustache(text)
    engine.layout("notused", template, templParams)
  }
}

case class TemplateProps(username: String, botTaskIdent: String) {
  def toMap: Map[String, String] =
    Map("username" -> username,
      "botTaskIdent" -> botTaskIdent)
}
