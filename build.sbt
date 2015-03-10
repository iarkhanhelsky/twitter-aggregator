name := "twitter-aggregator"

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

// Cli args parse
libraryDependencies += "com.github.scopt" %% "scopt" % "3.3.0"

resolvers += Resolver.sonatypeRepo("public")

// AspectJ
libraryDependencies += "org.aspectj" % "aspectjweaver" % "1.7.2"

libraryDependencies += "org.aspectj" % "aspectjrt" % "1.7.2"

// Kamon io
resolvers += "Kamon Repository" at "http://repo.kamon.io"

libraryDependencies += "kamon" %%  "kamon-spray" % "0.0.11"
