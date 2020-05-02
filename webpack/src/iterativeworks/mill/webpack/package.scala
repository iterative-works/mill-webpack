package iterativeworks.mill

package object webpack {
  implicit class NodeDepSyntax(ctx: StringContext) {
    private def parse(typed: Boolean)(args: Seq[Any]): NodeDep =
      NodeDep.parse({
        (
          ctx.parts.take(args.length).zip(args).flatMap {
            case (p, a) => Seq(p, a)
          } ++
            ctx.parts.drop(args.length)
        ).mkString
      }, typed)
    def js(args: Any*): NodeDep = parse(false)(args)
    def ts(args: Any*): NodeDep = parse(true)(args)
  }

}
