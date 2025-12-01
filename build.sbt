ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.7.4"

name := "s3bench2"

lazy val circeVersion  = "0.14.15"
lazy val http4sVersion = "1.0.0-M46"

fork := false

libraryDependencies ++= Seq(
  "org.typelevel"          %% "cats-effect"         % "3.6.3",
  "ch.qos.logback"          % "logback-classic"     % "1.5.21",
  "co.fs2"                 %% "fs2-core"            % "3.12.2",
  "co.fs2"                 %% "fs2-io"              % "3.12.2",
  "org.scalatest"          %% "scalatest"           % "3.2.19" % "test",
  "io.circe"               %% "circe-core"          % circeVersion,
  "io.circe"               %% "circe-generic"       % circeVersion,
  "io.circe"               %% "circe-parser"        % circeVersion,
  "org.http4s"             %% "http4s-ember-client" % http4sVersion,
  "org.http4s"             %% "http4s-dsl"          % http4sVersion,
  "org.typelevel"          %% "log4cats-slf4j"      % "2.7.1",
  "org.rogach"             %% "scallop"             % "6.0.0",
  "org.scala-lang.modules" %% "scala-xml"           % "2.4.0",
  "org.apache.commons"      % "commons-math3"       % "3.6.1"
)

ThisBuild / assemblyMergeStrategy := {
  case PathList("module-info.class")         => MergeStrategy.discard
  case x if x.endsWith("/module-info.class") => MergeStrategy.discard
  case x                                     =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}
