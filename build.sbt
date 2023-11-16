val scala3Version = "3.3.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "ox-tryout",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "com.softwaremill.ox" %% "core" % "0.0.14",
      "org.scalameta" %% "munit" % "0.7.29" % Test
    )
  )
