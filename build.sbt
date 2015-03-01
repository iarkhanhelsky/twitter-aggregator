name := "scala-lab"

version := "1.0"

scalaVersion := "2.11.5"

// typesafe config
libraryDependencies += "com.typesafe" % "config" % "1.2.1"

// akka
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.6"

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

// spray-client
libraryDependencies += "io.spray" %% "spray-client" % "1.3.2"

libraryDependencies += "io.spray" %% "spray-httpx" % "1.3.2"

libraryDependencies += "io.spray" %% "spray-json" % "1.3.1"


