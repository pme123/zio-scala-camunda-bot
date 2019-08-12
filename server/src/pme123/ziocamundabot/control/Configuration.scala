package pme123.ziocamundabot.control

import pme123.ziocamundabot.entity.configuration.Config
import zio.{Task, TaskR}
import pureconfig._

trait Configuration extends Serializable {
  val config: Configuration.Service[Any]
}

object Configuration {

  trait Service[R] {
    val load: TaskR[R, Config]
  }

  trait Live extends Configuration {
    val config: Service[Any] = new Service[Any] {
      val load: Task[Config] = Task.effect(loadConfigOrThrow[Config])
    }
  }

  object Live extends Live

}
