val Http4sVersion = "0.18.0-M7"
val Specs2Version = "4.0.0"
val LogbackVersion = "1.2.3"

ensimeScalaVersion in ThisBuild := "2.12.4"

resolvers += Resolver.sonatypeRepo("releases")

resolvers += "peterbecich-twitter4s" at "https://packagecloud.io/peterbecich/twitter4s/maven2"

enablePlugins(DockerPlugin)

// https://github.com/marcuslonnberg/sbt-docker
imageNames in docker := Seq(
  // Sets the latest tag
  ImageName(s"peterbecich/${name.value}:latest")
)

parallelExecution in ThisBuild := false

mainClass in assembly := Some("me.peterbecich.bannodemo.HelloWorldServer")

dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("java")
    add(artifact, artifactTargetPath)
    copy(baseDirectory(_ / "BannoDemo-frontend" / "static").value, file("/srv/static"))
    copy(baseDirectory(_ / "src" / "main" / "resources" / "emoji-data" ).value, file("/srv/emoji-data"))
    entryPoint("java", "-jar", artifactTargetPath)
  }
}

val circe = "0.9.0"
// val Json4s = "3.5.3"
val Json4s = "3.6.0-M2"
val fs2 = "0.10.0-M11"

lazy val root = (project in file("."))
  .settings(
    organization := "me.peterbecich",
    name := "bannodemo",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.12.4",
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"        % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "org.http4s"      %% "http4s-blaze-client"          % Http4sVersion,
      "io.circe" % "circe-generic_2.12" % circe,
      "io.circe" % "circe-literal_2.12" % circe,
      "co.fs2" %% "fs2-core" % fs2,
      "co.fs2" %% "fs2-io" % fs2,
      "org.typelevel" %% "cats-core" % "1.0.0",
      // "com.danielasfregola" %% "twitter4s" % "5.3",
      // "com.danielasfregola" %% "twitter4s" % "5.5-FS2",
      "me.peterbecich" % "twitter4s" % "5.5-FS2",
      "org.specs2"     %% "specs2-core"          % Specs2Version % "test",
      "ch.qos.logback"  %  "logback-classic"     % LogbackVersion,
      "com.typesafe" % "config" % "1.3.1",
      //"org.apache.commons" % "commons-collections4" % "4.1",
      "commons-collections" % "commons-collections" % "3.2.2",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
      "de.heikoseeberger" %% "akka-http-circe" % "1.18.0",
      "de.heikoseeberger" %% "akka-http-json4s" % "1.18.0",
      "org.json4s" %% "json4s-native" % Json4s,
      "org.json4s" %% "json4s-ext" % Json4s,
      "org.scalactic" %% "scalactic" % "3.0.4",
      "org.scalatest" %% "scalatest" % "3.0.4" % "test",
      "org.scalacheck" %% "scalacheck" % "1.13.4" % "test"
    )
  )

