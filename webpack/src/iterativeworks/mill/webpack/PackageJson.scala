package iterativeworks.mill.webpack

import upickle.default._

case class NodeDep(name: String, version: String)

object NodeDep

case class NodeDeps(deps: Seq[NodeDep])

object NodeDeps {
  implicit def rw: ReadWriter[NodeDeps] =
    readwriter[Map[String, String]].bimap(
      _.deps.map(d => d.name -> d.version).toMap,
      d =>
        NodeDeps(d.toSeq.map {
          case (name, version) => NodeDep(name, version)
        })
    )
}

case class PackageJson(dependencies: NodeDeps, devDependencies: NodeDeps)

object PackageJson {
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
          read[NodeDeps](json("dependencies")),
          read[NodeDeps](json("devDependencies"))
        )
    )
}
