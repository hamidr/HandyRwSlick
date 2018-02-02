name := "rw-slick"

version := "0.1"

scalaVersion := "2.12.4"
libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.2.1",
  "org.typelevel" %% "cats-core" % "1.0.1",
  "org.scalactic" %% "scalactic" % "3.0.4",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test",
  "org.scalamock" %% "scalamock" % "4.0.0" % Test,
  "org.mockito" % "mockito-core" % "2.10.0" % "test",
)

