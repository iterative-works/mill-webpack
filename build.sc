import mill._, scalalib._

object V {
  val millVersion = "0.6.2"
  val scalaVersion = "2.12.10"
}

object Deps {
  val millMain = ivy"com.lihaoyi::mill-main:${V.millVersion}"
  val millMainApi = ivy"com.lihaoyi::mill-main-api:${V.millVersion}"
  val millScalalib = ivy"com.lihaoyi::mill-scalalib:${V.millVersion}"
  val millScalalibApi = ivy"com.lihaoyi::mill-scalalib-api:${V.millVersion}"
  val millScalajslib = ivy"com.lihaoyi::mill-scalajslib:${V.millVersion}"
  val millScalajslibApi = ivy"com.lihaoyi::mill-scalajslib-api:${V.millVersion}"
}

object webpack extends ScalaModule {
  def scalaVersion = V.scalaVersion
  def ivyDeps = Agg(
    Deps.millMainApi
  )

  object test extends Tests {
    def testFrameworks = Seq("org.scalatest.tools.Framework")
    def ivyDeps =
      Agg(ivy"org.scalatest::scalatest::3.0.8")
  }
}
