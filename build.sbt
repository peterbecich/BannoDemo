val Http4sVersion = "0.18.0-M5"
val Specs2Version = "4.0.0"
val LogbackVersion = "1.2.3"

ensimeScalaVersion in ThisBuild := "2.12.4"

resolvers += Resolver.sonatypeRepo("releases")

enablePlugins(DockerPlugin)

// https://github.com/marcuslonnberg/sbt-docker
imageNames in docker := Seq(
  // Sets the latest tag
  ImageName(s"peterbecich/${name.value}:latest")
)

dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("java")
    add(artifact, artifactTargetPath)
    entryPoint("java", "-jar", artifactTargetPath)
  }
}

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
      "io.circe" % "circe-generic_2.12" % "0.9.0-M3",
      "io.circe" % "circe-literal_2.12" % "0.9.0-M3",
      "co.fs2" %% "fs2-core" % "0.10.0-M8",
      "co.fs2" %% "fs2-io" % "0.10.0-M8",
      "org.typelevel" %% "cats-core" % "1.0.0-RC2",
      "com.danielasfregola" %% "twitter4s" % "5.3",
      "org.specs2"     %% "specs2-core"          % Specs2Version % "test",
      "ch.qos.logback"  %  "logback-classic"     % LogbackVersion,
      "com.typesafe" % "config" % "1.3.1"
    )
  )

