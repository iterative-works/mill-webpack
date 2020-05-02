package iterativeworks.mill.webpack

import org.scalatest.WordSpec
import java.nio.file.Path

class PackageJsonTest extends WordSpec {
  "Package json" should {
    "serialize into json file" in {
      val existingFile =
        ujson.read(Path.of(getClass.getResource("/package.json").toURI))
      val example = PackageJson(
        NodeDeps(
          Seq(
            NodeDep("@types/react", "16.9.32"),
            NodeDep("antd-mobile", "2.3.1"),
            NodeDep("react", "16.12.0"),
            NodeDep("react-dom", "16.12.0"),
            NodeDep("react-proxy", "1.1.8"),
            NodeDep("typescript", "3.8")
          )
        ),
        NodeDeps(
          Seq(
            NodeDep("@storybook/addon-actions", "^5.3.18"),
            NodeDep("@storybook/react", "^5.3.18"),
            NodeDep("@babel/core", "^7.9.6"),
            NodeDep("@emotion/core", "^10.0.28"),
            NodeDep("babel-loader", "^8.1.0"),
            NodeDep("concat-with-sourcemaps", "1.0.7"),
            NodeDep("copy-webpack-plugin", "5.0.5"),
            NodeDep("css-loader", "3.2.0"),
            NodeDep("file-loader", "5.0.2"),
            NodeDep("html-webpack-plugin", "3.2.0"),
            NodeDep("imports-loader", "0.8.0"),
            NodeDep("source-map-loader", "0.2.3"),
            NodeDep("style-loader", "1.0.0"),
            NodeDep("url-loader", "3.0.0"),
            NodeDep("webpack", "4.41.2"),
            NodeDep("webpack-cli", "3.3.2"),
            NodeDep("webpack-dev-server", "3.9.0"),
            NodeDep("webpack-merge", "4.2.2")
          )
        )
      )

      assert(upickle.default.writeJs(example) === existingFile)
    }
  }
}
