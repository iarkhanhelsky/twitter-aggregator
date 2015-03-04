package org.scala.lab.twitter

import akka.actor.{Actor, ActorSystem, Props}
import akka.io.IO
import com.typesafe.config.ConfigFactory
import core.OAuth
import core.OAuth._
import org.scala.lab.twitter.Configuration._
import org.scala.lab.twitter.TwitterDefines.twitterStreamingUri
import spray.can.Http
import spray.client.pipelining._
import spray.http._

case class ArgsConfig(authJson: String = "auth.json")

object TwiAgg extends App {
  override def main(args: Array[String]): Unit = {
    super.main(args)

    val parser = new scopt.OptionParser[ArgsConfig]("twitter-aggregator") {
      head("twitter-aggregator")
      opt[String]('A', "auth-json") valueName ("<file>") action { (x, c) =>
        c.copy(authJson = x)
      } text ("optional auth file to override auth settings \n" +
        "format is following: \n" +
        "{\n  \"twitter\" : {\n    \"auth\" : {\n      \"key\" : <consumer-key>,\n      \"secret\" : <consumer-secret>,\n" +
        "      \"token\" : <access-token>,\n      \"token-secret\" : <access-token-secret>\n    }\n  }\n}")
    }

    parser.parse(args, ArgsConfig()) match {
      case Some(argsConf) =>
        //
        val fallback = ConfigFactory.load(argsConf.authJson)

        // Read config
        val config = readConfig(
          ConfigFactory.load("application.json").withFallback(fallback))

        // From Twitter Developer Console
        // Consumer key-secret pair for OAuth
        val consumer = Consumer(config.twitter.auth.key, config.twitter.auth.secret)
        // Token for OAuth
        val token = Token(config.twitter.auth.token, config.twitter.auth.tokenSecret)

        case class AuthPair(consumer: Consumer, token: Token);

        val authPair = AuthPair(consumer, token)

        // Init actor system
        val system = ActorSystem("twitter")

        // Create twitter stream
        val stream = system
          .actorOf(Props(
          new TweetStreamerActor(twitterStreamingUri, consumer, token)
            with OAuthTwitterAuthorization {

            override def consumer: Consumer = authPair.consumer
            override def token: Token = authPair.token

          }))

        // Run query
        stream ! config.twitter.queries.iterator.next()

      case None =>
        // do nothing. will print help
    }
  }

  sys.addShutdownHook(shutdown)

  def shutdown(): Unit = {
    println("Bye!")
  }
}
