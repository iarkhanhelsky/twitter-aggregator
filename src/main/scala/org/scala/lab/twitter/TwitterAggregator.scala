package org.scala.lab.twitter

import akka.actor._
import akka.io.IO
import com.typesafe.config.ConfigFactory
import core.OAuth._
import org.scala.lab.twitter.Configuration._
import spray.can.Http

case class ArgsConfig(authJson: String = "auth.json")

object TwitterAggregator extends App {
  override def main(args: Array[String]): Unit = {
    super.main(args)

    val parser = new scopt.OptionParser[ArgsConfig]("twitter-aggregator") {
      head("twitter-aggregator")
      opt[String]('A', "auth-json") valueName ("<file>") action { (x, c) =>
        c.copy(authJson = x)
      } text ("optional auth file to override auth settings \n" +
        "format is following: \n" +
        "{\n" +
        "  \"twitter\" : {\n" +
        "    \"auth\" : {\n" +
        "      \"key\" : <consumer-key>,\n" +
        "      \"secret\" : <consumer-secret>,\n" +
        "      \"token\" : <access-token>,\n" +
        "      \"token-secret\" : <access-token-secret>\n" +
        "    }\n" +
        "  }\n" +
        "}")
    }

    parser.parse(args, ArgsConfig()) match {
      case Some(argsConf) =>
        // fallback config
        val fallback = ConfigFactory.load(argsConf.authJson)

        // Read config
        val config = readConfig(ConfigFactory.load("application.json").withFallback(fallback))

        val authPair = AuthPair(
          // From Twitter Developer Console
          // Consumer key-secret pair for OAuth
          Consumer(config.twitter.auth.key, config.twitter.auth.secret),
          // Token for OAuth
          Token(config.twitter.auth.token, config.twitter.auth.tokenSecret))

        // Init actor system
        val system = ActorSystem(config.actors.systemName)
        val http = IO(Http)(system)

        // Create aggregator
        val aggregator = system.actorOf(
            Props(new AggregatorList(
              system.actorOf(Props(new PrintAggregator())),
              system.actorOf(Props(new HistogramAggregator()))
            ))
        )

        // Create twitter stream
        val stream = system
          .actorOf(Props(
          new TweetStreamerActor(http, aggregator)
            with OAuthTwitterAuthorization {

            override def consumer: Consumer = authPair.consumer
            override def token: Token = authPair.token

          }))

        // Run queries
        config.twitter.queries.foreach(stream ! _)

        // Add shutdown hook
        sys.addShutdownHook({
          println("")
          system.shutdown()
          system.awaitTermination()
        })

      case None =>
        // do nothing. will print help
    }
  }
}
