package pme123.ziocamundabot

trait AppException extends Throwable {
  def msg: String

  def cause: Option[Throwable] = None

  override def getMessage: String = msg

  override def getCause: Throwable = cause.orNull
}
