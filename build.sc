import mill._, scalalib._
import coursier.maven.MavenRepository

object V {
  val mill = "0.6.2"
  val scala = "2.12.10"
  val scalablytypedConverter = "1.0.0-beta12"
}

object Deps {
  val millMain = ivy"com.lihaoyi::mill-main:${V.mill}"
  val millMainApi = ivy"com.lihaoyi::mill-main-api:${V.mill}"
  val millScalalib = ivy"com.lihaoyi::mill-scalalib:${V.mill}"
  val millScalalibApi = ivy"com.lihaoyi::mill-scalalib-api:${V.mill}"
  val millScalajslib = ivy"com.lihaoyi::mill-scalajslib:${V.mill}"
  val millScalajslibApi = ivy"com.lihaoyi::mill-scalajslib-api:${V.mill}"
  val scalablytypedConverter =
    ivy"org.scalablytyped.converter::importer-portable:${V.scalablytypedConverter}"
}

object webpack extends ScalaModule {
  def scalaVersion = V.scala
  def ivyDeps = Agg(
    Deps.millMainApi,
    Deps.millMain,
    Deps.scalablytypedConverter
  )

  def repositories = super.repositories ++ Seq(
    MavenRepository("https://dl.bintray.com/oyvindberg/converter/")
  )

  object test extends Tests {
    def testFrameworks = Seq("org.scalatest.tools.Framework")
    def ivyDeps =
      Agg(ivy"org.scalatest::scalatest::3.0.8")
  }
}
