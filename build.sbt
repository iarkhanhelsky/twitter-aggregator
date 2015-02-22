name := "scala-lab"

version := "1.0"

scalaVersion := "2.11.5"

// typesafe config
libraryDependencies += "com.typesafe" % "config" % "1.2.1"

// akka
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4-SNAPSHOT"

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

