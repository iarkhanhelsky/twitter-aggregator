package org.scala.lab.twitter

import akka.actor.{Props, ActorSystem, Actor, ActorRef}
import akka.io.IO
import com.typesafe.config.ConfigFactory
import core.OAuth
import core.OAuth._

import org.scala.lab.twitter.Configuration._
import spray.can.Http
import spray.client.pipelining._
import spray.http._


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

    case MessageChunk(entity, _) => println(entity)

    case HttpResponse(StatusCodes.Unauthorized, _ , _, _) =>
      println("Shutting down with error : unauthorized")
      context.system.shutdown()
      System.exit(401)

    case other =>
      println(s"Unexpected receive $other")
      System.exit(500)
  }
}


object TwiAgg {
  def main(args: Array[String]): Unit = {
    // Read config
    val config = readConfig(
      ConfigFactory.load("application.json").withFallback(ConfigFactory.load("auth.json"))) // fallback to stubbed file
                                                                                            // which not commited

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
  }
}
