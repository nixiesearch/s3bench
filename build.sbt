import sbt.Package.ManifestAttributes

version := "0.1.0-SNAPSHOT"

scalaVersion := "3.5.0"

name := "s3bench"

lazy val awsVersion = "2.26.31"
lazy val fs2Version = "3.11.0"

libraryDependencies ++= Seq(
  "org.typelevel"         %% "cats-effect"          % "3.5.4",
  "ch.qos.logback"         % "logback-classic"      % "1.5.8",
  "org.rogach"            %% "scallop"              % "5.1.0",
  "software.amazon.awssdk" % "s3"                   % awsVersion,
  "co.fs2"                %% "fs2-core"             % fs2Version,
  "co.fs2"                %% "fs2-io"               % fs2Version,
  "co.fs2"                %% "fs2-reactive-streams" % fs2Version,
  "org.apache.commons"     % "commons-math3"        % "3.6.1"
)

ThisBuild / assemblyMergeStrategy := {
  case PathList("module-info.class")                                         => MergeStrategy.discard
  case "META-INF/io.netty.versions.properties"                               => MergeStrategy.first
  case "META-INF/MANIFEST.MF"                                                => MergeStrategy.discard
  case x if x.startsWith("META-INF/versions/")                               => MergeStrategy.first
  case x if x.startsWith("META-INF/services/")                               => MergeStrategy.concat
  case "META-INF/native-image/reflect-config.json"                           => MergeStrategy.concat
  case "META-INF/native-image/io.netty/netty-common/native-image.properties" => MergeStrategy.first
  case "META-INF/okio.kotlin_module"                                         => MergeStrategy.first
  case "findbugsExclude.xml"                                                 => MergeStrategy.discard
  case x if x.endsWith("/module-info.class")                                 => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

assembly / assemblyJarName          := "s3bench.jar"
ThisBuild / assemblyRepeatableBuild := false
ThisBuild / usePipelining           := true
packageOptions                      := Seq(ManifestAttributes(("Multi-Release", "true")))

Compile / mainClass := Some("ai.nixiesearch.s3bench.Main")

Compile / discoveredMainClasses := Seq()
