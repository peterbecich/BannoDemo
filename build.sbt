val Http4sVersion = "0.18.0-M5"
val Specs2Version = "4.0.0"
val LogbackVersion = "1.2.3"

ensimeScalaVersion in ThisBuild := "2.12.4"

resolvers += Resolver.sonatypeRepo("releases")

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
      "com.danielasfregola" %% "twitter4s" % "5.3",
      "org.specs2"     %% "specs2-core"          % Specs2Version % "test",
      "ch.qos.logback"  %  "logback-classic"     % LogbackVersion,
      "com.typesafe" % "config" % "1.3.1"
    )
  )

