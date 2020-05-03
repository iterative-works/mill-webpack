package iterativeworks.mill.webpack

import mill._
import upickle.default._

case class NodeDep(name: String, version: String, typed: Boolean = false)

object NodeDep {
  def parse(s: String, typed: Boolean): NodeDep = {
    s.split(":") match {
      case Array(name, version) => NodeDep(name, version, typed)
      case _                    => throw new Exception(s"Unable to parse node dependency: '$s'")
    }
  }

  implicit def rw: ReadWriter[NodeDep] = upickle.default.macroRW
}

case class NodeDeps(deps: Seq[NodeDep])

case class PackageJson(
    dependencies: Agg[NodeDep],
    devDependencies: Agg[NodeDep]
)

object PackageJson {
  implicit def rwDeps: ReadWriter[Agg[NodeDep]] =
    readwriter[ujson.Obj].bimap(
      deps =>
        // Keep stable sort to not trigger changes accidentally
        ujson.Obj.from(
          deps.toSeq.sortBy(_.name).map(d => d.name -> ujson.Str(d.version))
        ),
      d =>
        Agg.from(d.value.toSeq.map {
          case (name, ujson.Str(version)) => NodeDep(name, version, false)
          case dep @ _ =>
            throw new Exception(s"Invalid dependencies field: $dep")
        })
    )

  implicit def rw: ReadWriter[PackageJson] =
    readwriter[ujson.Obj].bimap(
      pj =>
        ujson.Obj(
          "private" -> true,
          "license" -> "UNLICENSED",
          "dependencies" -> writeJs(pj.dependencies),
          "devDependencies" -> writeJs(pj.devDependencies)
        ),
      json =>
        PackageJson(
          read[Agg[NodeDep]](json("dependencies")),
          read[Agg[NodeDep]](json("devDependencies"))
        )
    )
}
