import DependecyVersions._
import Releasing._
import Documentation._
import bloop.integrations.sbt.BloopDefaults
import sbt.util

lazy val twoTwelve              = "2.12.17"
lazy val twoThirteen            = "2.13.10"
lazy val three                  = "3.2.1"
lazy val supportedScalaVersions = Seq(twoTwelve, twoThirteen, three)

ThisBuild / organization := "com.github.mjakubowski84"
ThisBuild / version := "2.11.0-SNAPSHOT"
ThisBuild / isSnapshot := true
ThisBuild / scalaVersion := twoThirteen

ThisBuild / javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
ThisBuild / resolvers := Opts.resolver.sonatypeOssReleases :+ Resolver.jcenterRepo

ThisBuild / makePomConfiguration := makePomConfiguration.value.withConfigurations(
  Configurations.defaultMavenConfigurations
)
ThisBuild / versionScheme := Some("semver-spec")
Global / excludeLintKeys ++= Set(
  run / cancelable,
  IntegrationTest / publishArtifact,
  makePomConfiguration,
  publish / parallelExecution,
  publishLocal / parallelExecution
)

lazy val compilationSettings = Seq(
  scalacOptions ++= {
    Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-language:implicitConversions",
      "-Xfatal-warnings"
    ) ++ {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) =>
          Seq(
            "-unchecked",
            "-release:8"
          )
        case _ =>
          Seq(
            "-deprecation",
            "-Xsource:3",
            "-release:8"
          )
      }
    }
  }
)

lazy val itSettings = Defaults.itSettings ++
  Project.inConfig(IntegrationTest)(
    Seq(
      fork := true,
      parallelExecution := false,
      testOptions += Tests.Argument("-u", "target/junit/" + scalaBinaryVersion.value)
    )
  ) ++
  Project.inConfig(IntegrationTest)(BloopDefaults.configSettings)

lazy val testReportSettings = Project.inConfig(Test)(
  Seq(
    testOptions += Tests.Argument("-u", "target/junit/" + scalaBinaryVersion.value)
  )
)

// used only for testing in core module
lazy val sparkDeps = Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion % "it"
    exclude (org = "org.apache.hadoop", name = "hadoop-client")
    exclude (org = "org.apache.hadoop", name = "hadoop-client-api")
    exclude (org = "org.slf4j", name = "slf4j-api")
    exclude (org = "org.apache.parquet", name = "parquet-hadoop"),
  "org.apache.spark" %% "spark-sql" % sparkVersion % "it"
    exclude (org = "org.apache.hadoop", name = "hadoop-client")
)

lazy val core = (project in file("core"))
  .configs(IntegrationTest)
  .settings(
    name := "parquet4s-core",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "org.apache.parquet" % "parquet-hadoop" % parquetVersion
        exclude (org = "org.slf4j", name = "slf4j-api"),
      "org.apache.hadoop" % "hadoop-client" % hadoopVersion % Provided,
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionCompatVersion,

      // tests
      "org.mockito" % "mockito-core" % mockitoVersion % "test",
      "org.scalatest" %% "scalatest" % scalatestVersion % "test,it",
      "ch.qos.logback" % "logback-classic" % logbackVersion % "test,it",
      "org.slf4j" % "log4j-over-slf4j" % slf4jVersion % "test,it"
    ) ++ {
      CrossVersion.partialVersion(scalaBinaryVersion.value) match {
        case Some((2, 12 | 13)) => sparkDeps :+ ("com.chuusai" %% "shapeless" % shapelessVersion)
        case _                  => Seq.empty
      }
    },
    excludeDependencies ++= Seq(
      ExclusionRule("org.slf4j", "slf4j-log4j12")
    )
  )
  .settings(compilationSettings)
  .settings(itSettings)
  .settings(publishSettings)
  .settings(testReportSettings)
  .dependsOn(testkit % "it->compile")

lazy val akka = (project in file("akka"))
  .configs(IntegrationTest)
  .settings(
    name := "parquet4s-akka",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "org.apache.hadoop" % "hadoop-client" % hadoopVersion % Provided
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("org.slf4j", "slf4j-log4j12")
    )
  )
  .settings(compilationSettings)
  .settings(itSettings)
  .settings(publishSettings)
  .settings(testReportSettings)
  .dependsOn(core % "compile->compile;it->it")

lazy val fs2 = (project in file("fs2"))
  .configs(IntegrationTest)
  .settings(
    name := "parquet4s-fs2",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.apache.hadoop" % "hadoop-client" % hadoopVersion % Provided,
      "co.fs2" %% "fs2-io" % fs2Version % "it",
      "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % "it"
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("org.slf4j", "slf4j-log4j12")
    )
  )
  .settings(compilationSettings)
  .settings(itSettings)
  .settings(publishSettings)
  .settings(testReportSettings)
  .dependsOn(core % "compile->compile;test->test", testkit % "it->compile")

lazy val testkit = (project in file("testkit"))
  .settings(
    name := "parquet4s-testkit",
    crossScalaVersions := supportedScalaVersions,
    publish / skip := true,
    publishLocal / skip := true,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestVersion,
      "org.apache.hadoop" % "hadoop-minicluster" % hadoopVersion,
      // MiniDFSCluster leaks Mockito via NameNodeAdapter while hadoop-minicluster doesn't bring
      // the dependency transitively. We have to add it explicitly to prevent ClassNotFoundException.
      "org.mockito" % "mockito-core" % mockitoVersion,
      "org.slf4j" % "log4j-over-slf4j" % slf4jVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion
    )
  )

lazy val examples = (project in file("examples"))
  .settings(
    name := "parquet4s-examples",
    crossScalaVersions := Seq(twoTwelve, twoThirteen),
    publish / skip := true,
    publishLocal / skip := true,
    libraryDependencies ++= Seq(
      "org.apache.hadoop" % "hadoop-client" % hadoopVersion,
      "org.apache.parquet" % "parquet-protobuf" % parquetVersion,
      "io.github.embeddedkafka" %% "embedded-kafka" % "3.3.1",
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "org.slf4j" % "log4j-over-slf4j" % slf4jVersion,
      "com.typesafe.akka" %% "akka-stream-kafka" % {
        if (scalaVersion.value == twoThirteen) { "3.0.1" } // non-licensed version
        else { "2.1.1" }
      },
      "com.github.fd4s" %% "fs2-kafka" % "2.5.0",
      "co.fs2" %% "fs2-io" % fs2Version
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("org.slf4j", "slf4j-log4j12")
    ),
    evictionErrorLevel := util.Level.Warn,
    run / cancelable := true,
    run / fork := true
  )
  .settings(compilationSettings)
  .dependsOn(akka, fs2)
  .enablePlugins(ProtobufPlugin)

lazy val coreBenchmarks = (project in file("coreBenchmarks"))
  .settings(
    name := "parquet4s-core-benchmarks",
    publish / skip := true,
    publishLocal / skip := true,
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "org.apache.hadoop" % "hadoop-client" % hadoopVersion,
      "org.slf4j" % "slf4j-nop" % slf4jVersion,
      "org.slf4j" % "log4j-over-slf4j" % slf4jVersion
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("org.slf4j", "slf4j-log4j12")
    ),
    run / cancelable := true,
    run / fork := true
  )
  .settings(compilationSettings)
  .enablePlugins(JmhPlugin)
  .dependsOn(core)

lazy val akkaBenchmarks = (project in file("akkaBenchmarks"))
  .settings(
    name := "parquet4s-akka-benchmarks",
    publish / skip := true,
    publishLocal / skip := true,
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "org.apache.hadoop" % "hadoop-client" % hadoopVersion,
      "org.slf4j" % "slf4j-nop" % slf4jVersion,
      "org.slf4j" % "log4j-over-slf4j" % slf4jVersion
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("org.slf4j", "slf4j-log4j12")
    ),
    run / cancelable := true,
    run / fork := true
  )
  .settings(compilationSettings)
  .enablePlugins(JmhPlugin)
  .dependsOn(akka)

lazy val fs2Benchmarks = (project in file("fs2Benchmarks"))
  .settings(
    name := "parquet4s-fs2-benchmarks",
    publish / skip := true,
    publishLocal / skip := true,
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "org.apache.hadoop" % "hadoop-client" % hadoopVersion,
      "org.slf4j" % "slf4j-nop" % slf4jVersion,
      "org.slf4j" % "log4j-over-slf4j" % slf4jVersion
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("org.slf4j", "slf4j-log4j12")
    ),
    run / cancelable := true,
    run / fork := true
  )
  .settings(compilationSettings)
  .enablePlugins(JmhPlugin)
  .dependsOn(fs2)

lazy val documentation = (project in file("site"))
  .settings(documentationSettings)
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "mdoc" % "2.3.6",
      "org.apache.hadoop" % "hadoop-client" % hadoopVersion,
      "org.slf4j" % "slf4j-nop" % slf4jVersion,
      "org.slf4j" % "log4j-over-slf4j" % slf4jVersion
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("org.scala-lang.modules", "scala-collection-compat_2.13")
    ),
    dependencyOverrides ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionCompatVersion
    )
  )
  .dependsOn(core, akka, fs2)
  .enablePlugins(MicrositesPlugin)

lazy val root = (project in file("."))
  .settings(publishSettings)
  .settings(
    crossScalaVersions := Nil,
    publish / skip := true,
    publish / parallelExecution := false,
    publishLocal / skip := true,
    publishLocal / parallelExecution := false
  )
  .aggregate(
    core,
    akka,
    fs2,
    testkit,
    examples,
    coreBenchmarks,
    akkaBenchmarks,
    fs2Benchmarks,
    documentation
  )
