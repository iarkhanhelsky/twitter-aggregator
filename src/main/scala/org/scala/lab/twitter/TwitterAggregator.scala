package org.scala.lab.twitter

import akka.actor._
import akka.io.IO
import com.typesafe.config.ConfigFactory
import core.OAuth._
import org.scala.lab.twitter.Configuration._
import org.scala.lab.twitter.TwitterDefines.Tweet
import spray.can.Http

case class ArgsConfig(authJson: String = "auth.json")

class PrintAggregator extends Actor {
  override def receive: Receive = {
    case (query: String, tweet : Tweet) =>
      println(s"query: %${query.length}s :: @%-20s >  %s".format(query, tweet.user.username, tweet.text))
    case unexpected =>
      // TODO: Do something with unexpected message
      // TODO: Or not?
  }
}

class HistogramAggregator extends Actor {

  var factorCountMap = Map[String, Int]()

  override def receive: Actor.Receive = {
    case (query: String, _: Tweet) =>
     val hits = factorCountMap.contains(query) match {
       case true =>
         factorCountMap(query)
       case false =>
         0
     }

      factorCountMap = factorCountMap ++ Map(query -> (hits + 1))
    case unexpected =>
      // TODO: Do something with unexpected message
      // TODO: Or not?
  }

  override def aroundPostStop(): Unit = {
    val (totalTweets, maxQueryLength) = factorCountMap.foldLeft((0, 0))((acc: (Int, Int), kv: (String, Int)) => (acc._1 + kv._2, math.max(acc._2, kv._1.length)))
    factorCountMap.foreach( (pair : (String, Int)) => {
      val (query, hits) = pair
      // Max string length set to 80
      // TODO: Get terminal width?
      val maxStringLength = 80

      val barLength = maxStringLength - // total string length
       maxQueryLength - // query length
       2 - // two spaces
       2 - // two brackets
       math.log10(totalTweets) // digits in tweets amount

      val barCount: Int = (hits * (barLength / totalTweets)).toInt
      val bar: String = Seq.range(0, barCount).foldLeft("")((acc: String, _) => acc + "|")
      println((s"%${maxQueryLength}s [%-${barLength}s] %d").format(query, bar, hits))
    })

    println(s"Total: ${totalTweets}")
  }
}

class AggregatorsList(actors: ActorRef*) extends Actor {
  override def receive: Actor.Receive = {
    case message =>
      actors.foreach( _ ! message)
  }
}

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
            Props(new AggregatorsList(
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
