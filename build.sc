// build.sc

import mill._, scalalib._

object Version {
  val scalaVersion = "2.12.8"

  val cats = "2.0.0"
  val circe = "0.12.1"
  val http4s = "0.21.1"
  val sttp = "1.6.4"
  val zio = "1.0.0-RC18"
  val zioCats = "2.0.0.0-RC11"

  val pureConfig = "0.12.0"
  val telegram = "4.4.0-RC2"
  val scalate = "1.9.1"
}

object Libs {

  val cats = ivy"org.typelevel::cats-core:${Version.cats}"
  val catsEffect = ivy"org.typelevel::cats-effect:${Version.cats}"
  val circeCore = ivy"io.circe::circe-core:${Version.circe}"
  val circeGeneric = ivy"io.circe::circe-generic:${Version.circe}"
  val http4sBlazeServer =
    ivy"org.http4s::http4s-blaze-server:${Version.http4s}"
  val http4sBlazeClient =
    ivy"org.http4s::http4s-blaze-client:${Version.http4s}"
  val http4sCirce = ivy"org.http4s::http4s-circe:${Version.http4s}"
  val http4sDsl = ivy"org.http4s::http4s-dsl:${Version.http4s}"
  val sttpCore = ivy"com.softwaremill.sttp::core:${Version.sttp}"
  val sttpClient =
    ivy"com.softwaremill.sttp::async-http-client-backend-zio:${Version.sttp}"
  val sttpPlayJson = ivy"com.softwaremill.sttp::play-json::${Version.sttp}"
  val zio = ivy"dev.zio::zio:${Version.zio}"
  val zioStream = ivy"dev.zio::zio-streams:${Version.zio}"
  val zioCats = ivy"dev.zio::zio-interop-cats:${Version.zioCats}"

  val pureConfig = ivy"com.github.pureconfig::pureconfig:${Version.pureConfig}"
  val telegram = ivy"com.bot4s::telegram-core:${Version.telegram}"
  val scalate = ivy"org.scalatra.scalate::scalate-core:${Version.scalate}"
}


object server extends ScalaModule {

  def scalaVersion = Version.scalaVersion

  override def ivyDeps = {
    Agg(
      Libs.catsEffect,
      Libs.pureConfig,
      Libs.zio,
      Libs.zioStream,
      Libs.zioCats,
      Libs.telegram,
      Libs.sttpCore,
      Libs.sttpClient,
      Libs.sttpPlayJson,
      Libs.scalate
    )
  }
}