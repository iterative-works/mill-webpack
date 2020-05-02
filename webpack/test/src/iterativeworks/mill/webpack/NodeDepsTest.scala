package iterativeworks.mill.webpack

import org.scalatest.WordSpec

class NodeDepsTest extends WordSpec {
  "Node deps" should {
    "parse node dependencies" in {
      val reactV = "16.12.0"
      val typedV = "16.9.32"
      assert(js"react-dom:16.12.0" === NodeDep("react-dom", "16.12.0", false))
      assert(
        ts"@types/react:16.9.32" === NodeDep("@types/react", "16.9.32", true)
      )
      assert(js"react-dom:$reactV" === NodeDep("react-dom", "16.12.0", false))
      assert(
        ts"@types/react:$typedV" === NodeDep("@types/react", "16.9.32", true)
      )
    }
  }
}
