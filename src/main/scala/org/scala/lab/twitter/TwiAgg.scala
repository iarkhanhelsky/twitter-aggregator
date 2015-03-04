package org.scala.lab.twitter

import akka.actor.{Props, ActorSystem, Actor, ActorRef}
import akka.io.IO
import com.typesafe.config.ConfigFactory
import core.OAuth
import core.OAuth._

import org.scala.lab.twitter.Configuration._
import spray.can.Http
import spray.client.pipelining._
import spray.http.HttpData.Bytes
import spray.http._

import scala.reflect.io.File


trait TwitterAuthorization {
  def authorize: HttpRequest => HttpRequest
}

trait OAuthTwitterAuthorization extends TwitterAuthorization {
  import OAuth._

  def consumer: Consumer = ???
  def token: Token = ???

  val authorize: (HttpRequest) => HttpRequest = oAuthAuthorizer(consumer, token)
}

object TweetStreamerActor {
  val twitterUri = Uri("https://stream.twitter.com/1.1/statuses/filter.json")
}

class TweetStreamerActor(uri: Uri, consumer : Consumer, token : Token) extends Actor {
  this: TwitterAuthorization =>
  val io = IO(Http)(context.system)

  def receive: Receive = {

    case query: String =>
      val body = HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`), s"track=$query")
      val rq = HttpRequest(HttpMethods.POST, uri = uri, entity = body) ~> authorize
      sendTo(io).withResponsesReceivedBy(self)(rq)
    case ChunkedResponseStart(_) =>

    case MessageChunk(entity, _) => println(new String(entity.toByteString.toArray))

    case HttpResponse(StatusCodes.Unauthorized, _ , _, _) =>
      println("Shutting down with error : unauthorized")
      context.system.shutdown()
      System.exit(401)

    case other =>
      println(s"Unexpected receive $other")
      System.exit(500)
  }
}

case class ArgsConfig(authJson : String = "auth.json")


object TwiAgg extends App {
  override def main(args: Array[String]): Unit = {
    super.main(args)

    val parser = new scopt.OptionParser[ArgsConfig]("twitter-aggregator") {
      head("twitter-aggregator")
      opt[String]('A', "auth-json") valueName("<file>") action { (x, c) =>
        c.copy(authJson = x) } text("optional auth file to override auth settings \n" +
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

        case class AuthPair(consumer : Consumer, token : Token);

        val authPair = AuthPair(consumer, token)

        // Init actor system
        val system = ActorSystem("twitter")

        // Create twitter stream
        val stream = system
          .actorOf(Props(
          new TweetStreamerActor(TweetStreamerActor.twitterUri, consumer, token)
            with OAuthTwitterAuthorization{
            override def consumer : Consumer = authPair.consumer
            override def token : Token = authPair.token
          }))

        // Run query
        stream ! config.twitter.queries.iterator.next()

      case None =>
        println("Got none")
    }
  }

  sys.addShutdownHook(shutdown)

  def shutdown() : Unit = {
    println("Bye!")
  }
}
