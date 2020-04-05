// build.sc

import mill._, scalalib._

object Version {
  val scalaVersion = "2.13.1"

  val cats = "2.0.0"
  val circe = "0.12.1"
  val http4s = "0.21.1"
  val sttp = "2.0.6"
  val zio = "1.0.0-RC18-2"
  val zioCats = "2.0.0.0-RC12"

  val pureConfig = "0.12.0"
  val telegramCanoe = "0.4.1"
  val scalate = "1.9.5"
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
  val sttpCore = ivy"com.softwaremill.sttp.client::core:${Version.sttp}"
  val sttpClient =
    ivy"com.softwaremill.sttp.client::async-http-client-backend-zio:${Version.sttp}"
  val sttpPlayJson = ivy"com.softwaremill.sttp.client::play-json::${Version.sttp}"
  val zio = ivy"dev.zio::zio:${Version.zio}"
  val zioStream = ivy"dev.zio::zio-streams:${Version.zio}"
  val zioCats = ivy"dev.zio::zio-interop-cats:${Version.zioCats}"

  val pureConfig = ivy"com.github.pureconfig::pureconfig:${Version.pureConfig}"
  val telegramCanoe = ivy"org.augustjune::canoe:${Version.telegramCanoe}"
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
      Libs.telegramCanoe,
      Libs.sttpCore,
      Libs.sttpClient,
      Libs.sttpPlayJson,
      Libs.scalate
    )
  }

  object test extends Tests {
    override def ivyDeps = Agg(
      ivy"dev.zio::zio-test:${Version.zio}",
      ivy"dev.zio::zio-test-sbt:${Version.zio}"
    )

    def testOne(args: String*) = T.command {
      super.runMain("org.scalatest.run", args: _*)
    }

    def testFrameworks =
      Seq("zio.test.sbt.ZTestFramework")
  }
}