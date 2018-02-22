name := "rw-slick"

version := "0.3.1"

isSnapshot := true
scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.2.1",
  "org.typelevel" %% "cats-core" % "1.0.1",
  "org.scalactic" %% "scalactic" % "3.0.4",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test",
  "org.scalamock" %% "scalamock" % "4.0.0" % Test,
  "org.mockito" % "mockito-core" % "2.10.0" % "test",
  "com.lihaoyi" %% "sourcecode" % "0.1.4", // Scala-JVM

)

organization := "com.snapptrip"
homepage := Some(url("https://github.com/hamidr/HandyRwSlick"))
pomExtra :=
  <scm>
    <connection>
      scm:git:git://github.com/hamidr/HandyRwSlick.git
    </connection>
    <url>
	https://github.com/hamidr/HandyRwSlick
    </url>
  </scm>
    <developers>
      <developer>
        <id>hamidr</id>
        <name>Hamidreza Davoodi</name>
        <email>hamidr.dev@gmail.com</email>
      </developer>
    </developers>

resolvers += "typesafe" at "http://repo.typesafe.com/typesafe/releases/"
resolvers += Resolver.jcenterRepo

publishTo := Some(
  "bintray" at
    "https://api.bintray.com/maven/hamidr/HandyRwSlick/HandyRwSlick/;publish=1")
credentials += Credentials(Path.userHome / ".bintray" / ".credentials")

publishMavenStyle := true

publishArtifact in Test := false

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
