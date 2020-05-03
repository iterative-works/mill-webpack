package iterativeworks.mill.webpack

import com.olvind.logging.{stdout, storing, LogLevel, Logger}
import mill._
import mill.scalajslib._
import org.scalablytyped.converter.{Flavour, Selection}
import org.scalablytyped.converter.internal.{
  constants,
  files,
  IArray,
  InFolder,
  Digest
}
import org.scalablytyped.converter.internal.importer._
import org.scalablytyped.converter.internal.phases.{
  PhaseListener,
  PhaseRes,
  PhaseRunner,
  RecPhase
}
import org.scalablytyped.converter.internal.scalajs.{Dep, Name, Versions}
import org.scalablytyped.converter.internal.ts.{
  CalculateLibraryVersion,
  TsIdentLibrary
}
import scala.collection.SortedMap

// TODO: make an external module object with Module trait as in Bloop.Module
// We could compute all the node jars once, and keep node_modules only once
trait NodeModule extends ScalaJSModule {
  def nodeModuleDeps: Seq[NodeModule] =
    moduleDeps.collect {
      case m: NodeModule => m
    }
  def transitiveNodeDeps: T[Agg[NodeDep]] = T {
    nodeDeps() ++ T.traverse(nodeModuleDeps)(_.transitiveNodeDeps)().flatten
  }
  def transitiveNodeDevDeps: T[Agg[NodeDep]] = {
    nodeDevDeps() ++ T
      .traverse(nodeModuleDeps)(_.transitiveNodeDevDeps)()
      .flatten
  }
  def nodeDeps: T[Agg[NodeDep]] = T(Agg.empty[NodeDep])
  def nodeDevDeps: T[Agg[NodeDep]] = T(Agg.empty[NodeDep])

  def typedNodeDeps: T[Agg[NodeDep]] = T(nodeDeps().filter(_.typed == true))
  def typedNodeDevDeps: T[Agg[NodeDep]] =
    T(nodeDevDeps().filter(_.typed == true))

  def transitiveTypedNodeDeps: T[Agg[NodeDep]] =
    T(transitiveNodeDeps().filter(_.typed == true))
  def transitiveTypedNodeDevDeps: T[Agg[NodeDep]] =
    T(transitiveNodeDevDeps().filter(_.typed == true))

  def typescriptVersion: T[String] = T("3.8")

  def packageJson: T[PackageJson] = T {
    PackageJson(
      transitiveNodeDeps() ++ Agg(js"typescript:${typescriptVersion()}"),
      transitiveNodeDevDeps()
    )
  }

  def writePackageJson = T {
    val outFile = millSourcePath / "package.json"
    os.write.over(outFile, upickle.default.write(packageJson(), 2))
    PathRef(outFile)
  }

  def yarnInstall = T {
    writePackageJson()
    os.proc("yarn", "install").call(millSourcePath)
    PathRef(millSourcePath / "yarn.lock")
  }

  def stUseScalaDom: T[Boolean] = T(false)
  def stFlavour: T[Flavour] = T(Flavour.Normal)
  def stOutputPackage: T[String] = T("typings")
  def stStdlib: T[List[String]] = T(List("es6"))
  def stIgnore: T[Agg[String]] = T(Agg("typescript"))

  def upstreamNodeTypings: T[Agg[scalalib.Dep]] = T {
    T.traverse(nodeModuleDeps)(_.nodeTypings)().flatten
  }

  // TODO: we need to have yarnInstall first.
  // TODO: external module that would create all the typings for all the node modules and recall just by name?
  // TODO: list all the typings that will happen
  def nodeTypings: T[Agg[scalalib.Dep]] = {
    def runTypings(
        folder: os.Path,
        destFolder: os.Path,
        conversion: ConversionOptions,
        wantedLibs: Agg[NodeDep],
        logger: Logger[Unit]
    ): Agg[scalalib.Dep] = {
      val fromNodeModules = Source
        .fromNodeModules(
          InFolder(folder / "node_modules"),
          conversion,
          wantedLibs.map(d => TsIdentLibrary(d.name)).toSet
        )

      // TODO: use mill logging
      println(
        s"Importing ${fromNodeModules.sources.map(_.libName.value).mkString(", ")}"
      )

      val parseCachePath =
        Some(files.existing(constants.defaultCacheFolder / 'parse).toNIO)

      val cachedParser =
        PersistingParser(parseCachePath, fromNodeModules.folders, logger.void)

      val flavour = flavourImpl.forConversion(conversion)

      val compiler: build.Compiler = new build.Compiler {
        def compile(
            name: String,
            digest: Digest,
            compilerPaths: build.CompilerPaths,
            deps: Set[build.Compiler.InternalDep],
            externalDeps: Set[Dep]
        ): Either[String, Unit] = {
          Left("Not implemented")
        }
      }

      val Phases: RecPhase[Source, build.PublishedSbtProject] = RecPhase[Source]
        .next(
          new Phase1ReadTypescript(
            resolve = fromNodeModules.libraryResolver,
            calculateLibraryVersion = CalculateLibraryVersion.PackageJsonOnly,
            ignored = conversion.ignoredLibs,
            ignoredModulePrefixes = conversion.ignoredModulePrefixes,
            stdlibSource = fromNodeModules.stdLibSource,
            pedantic = false,
            parser = cachedParser,
            expandTypeMappings = conversion.expandTypeMappings
          ),
          "typescript"
        )
        .next(
          new Phase2ToScalaJs(
            pedantic = false,
            enableScalaJsDefined = conversion.enableScalaJsDefined,
            outputPkg = conversion.outputPackage
          ),
          "scala.js"
        )
        .next(new PhaseFlavour(flavour), flavour.toString)
        .next(
          new Phase3Compile(
            versions = conversion.versions,
            compiler = compiler,
            targetFolder = destFolder / "sources",
            publishLocalFolder = constants.defaultLocalPublishFolder,
            flavour = flavour,
            organization = conversion.organization,
            publisherOpt = None,
            metadataFetcher = documentation.Npmjs.No,
            resolve = fromNodeModules.libraryResolver,
            softWrites = true,
            generateScalaJsBundlerFile = false,
            ensureSourceFilesWritten = false
          ),
          "build"
        )

      val results: Map[Source, PhaseRes[Source, build.PublishedSbtProject]] =
        fromNodeModules.sources
          .map(s =>
            s -> PhaseRunner(
              Phases,
              (_: Source) => logger.void,
              PhaseListener.NoListener
            )(s)
          )
          .toMap

      val successes: Map[Source, build.PublishedSbtProject] = {
        def go(
            source: Source,
            p: build.PublishedSbtProject
        ): Map[Source, build.PublishedSbtProject] =
          Map(source -> p) ++ p.project.deps.flatMap { case (k, v) => go(k, v) }

        results
          .collect { case (s, PhaseRes.Ok(res)) => go(s, res) }
          .reduceOption(_ ++ _)
          .getOrElse(Map.empty)
      }

      val failures: Map[Source, Either[Throwable, String]] =
        results
          .collect { case (_, PhaseRes.Failure(errors)) => errors }
          .reduceOption(_ ++ _)
          .getOrElse(Map.empty)

      def asDep(dep: Dep): scalalib.Dep =
        dep match {
          case Dep.Java(org, artifact, version) =>
            scalalib.Dep(
              org,
              artifact,
              version,
              cross = scalalib.CrossVersion.empty(platformed = false)
            )
          case Dep.Scala(org, artifact, version) =>
            scalalib.Dep(
              org,
              artifact,
              version,
              cross = scalalib.CrossVersion.Binary(platformed = false)
            )
          case Dep.ScalaJs(org, artifact, version) =>
            scalalib.Dep(
              org,
              artifact,
              version,
              cross = scalalib.CrossVersion.Binary(platformed = true)
            )
          case Dep.ScalaFullVersion(org, artifact, version) =>
            scalalib.Dep(
              org,
              artifact,
              version,
              cross = scalalib.CrossVersion.Full(platformed = false)
            )
        }

      if (failures.nonEmpty) {
        val messages = failures.foldLeft(List.empty[String])((acc, failure) => {
          val (source, err) = failure
          val msg = err.fold(_.getMessage, identity)
          acc :+ s"$source: $msg"
        })
        throw new Exception(
          s"Failed to convert typescript dependencies: $messages"
        )
      } else {
        Agg.from[scalalib.Dep](
          flavour.dependencies
            .map(asDep) ++ successes.map(_._2.project.reference).map(asDep)
        )
      }
    }

    T {
      upstreamNodeTypings()
      yarnInstall()

      // TODO: wrap logger around T.ctx.log, by sbt wrapper example
      val logger: Logger[(Array[Logger.Stored], Unit)] =
        storing() zipWith stdout.filter(LogLevel.warn)

      val conversion =
        ConversionOptions(
          useScalaJsDomTypes = stUseScalaDom(),
          flavour = stFlavour(),
          outputPackage = Name(stOutputPackage()),
          // TODO: make enableScalaJsDefined configurable
          enableScalaJsDefined = Selection.None,
          stdLibs = IArray.fromTraversable(stStdlib()),
          // TODO: make expandTypeMappings configurable
          expandTypeMappings = EnabledTypeMappingExpansion.DefaultSelection,
          ignoredLibs = stIgnore().map(TsIdentLibrary.apply).toSet,
          ignoredModulePrefixes = stIgnore().map(_.split("/").toList).toSet,
          versions = Versions(
            Versions.Scala(scalaVersion()),
            Versions.ScalaJs(scalaJSVersion())
          ),
          organization = "org.scalablytyped"
        )

      runTypings(
        millSourcePath,
        T.ctx().dest,
        conversion,
        typedNodeDeps(),
        logger.void
      )
    }
  }
}
