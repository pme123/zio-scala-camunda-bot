// build.sc

import mill._, scalalib._

object server extends ScalaModule {

  def scalaVersion = "2.12.8"

  val zioVersion = "1.0.0-RC9-4"
  val zioCatsVersion = "1.3.1.0-RC3"
  val sttpVersion = "1.6.3"

  override def ivyDeps = {
    Agg(
      ivy"com.github.pureconfig::pureconfig:0.11.0",
      ivy"dev.zio::zio:$zioVersion",
      ivy"dev.zio::zio-streams:$zioVersion",
      ivy"dev.zio::zio-interop-cats:$zioCatsVersion",
      ivy"org.typelevel::cats-effect:1.4.0",
      ivy"com.bot4s::telegram-core:4.3.0-RC1",
      ivy"com.softwaremill.sttp::core:$sttpVersion",
      ivy"com.softwaremill.sttp::async-http-client-backend-zio:$sttpVersion",
      ivy"com.softwaremill.sttp::play-json::$sttpVersion",
      ivy"org.scalatra.scalate::scalate-core:1.9.1"
    )
  }
}