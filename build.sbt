organization  := "io.buildo"

version       := "SNAPSHOT"

scalaVersion  := "2.11.7"

scalacOptions := Seq("-unchecked",
                     "-deprecation",
                     "-feature",
                     "-encoding", "utf8",
                     "-language:postfixOps",
                     "-language:implicitConversions",
                     "-language:higherKinds")

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/",
  "Sonatype Nexus Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Typesafe" at "http://repo.typesafe.com/typesafe/releases",
  "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
  "buildo mvn" at "https://raw.github.com/buildo/mvn/master/releases",
  "xuggle repo" at "http://xuggle.googlecode.com/svn/trunk/repo/share/java/"
)

libraryDependencies ++= {
  val akkaV = "2.3.9"
  val sprayV = "1.3.3"
  Seq(
    "io.buildo"           %%  "ingredients-jsend" % "0.3",
    "io.buildo"           %%  "base"            % "0.4.0",
    "com.typesafe.slick"  %% "slick-hikaricp"               % "3.1.0-RC2",
    "com.typesafe.slick"  %% "slick"               % "3.1.0-RC2",
    "mysql"               % "mysql-connector-java" % "5.1.25",
    "com.github.nscala-time" %% "nscala-time" % "2.6.0",
    "com.github.tototoshi" %% "slick-joda-mapper" % "2.1.0"
  )
}
