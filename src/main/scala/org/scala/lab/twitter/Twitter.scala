package org.scala.lab.twitter

import akka.actor.{Actor, ActorRef, Props}
import org.scala.lab.twitter.TwitterDefines._
import spray.client.pipelining._
import spray.http._
import spray.json._

// Provides basic authorization method
trait TwitterAuthorization {
  def authorize: HttpRequest => HttpRequest
}

// Defines oAuth scheme
trait OAuthTwitterAuthorization extends TwitterAuthorization {

  import core.OAuth._

  def consumer: Consumer = ???

  def token: Token = ???

  val authorize: (HttpRequest) => HttpRequest = oAuthAuthorizer(consumer, token)
}

// Defines base twitter service constants 
// And behaviours 
object TwitterDefines {
  val twitterStreamingUri = Uri("https://stream.twitter.com/1.1/statuses/filter.json")

  case class TwitterUser(id: Long, username: String)
  case class Tweet(id: Long, text: String, createdAt: String, user: TwitterUser)

  object TweetJsonProtocol extends DefaultJsonProtocol {
    implicit val user = jsonFormat(TwitterUser, "id", "screen_name")
    implicit val tweet = jsonFormat(Tweet, "id", "text", "created_at", "user")
  }
}

// Base streaming actor which listens for query responses
class TweetStreamerActor(io: ActorRef, aggregator: ActorRef) extends Actor {

  auth: TwitterAuthorization =>

  def receive: Receive = {
    case query: String =>
      val body = HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`), s"track=$query")
      val rq = HttpRequest(HttpMethods.POST, uri = twitterStreamingUri, entity = body) ~> authorize
      sendTo(io).withResponsesReceivedBy(context.actorOf(Props(new TwitterQueryActor(query, aggregator))))(rq)

    case HttpResponse(StatusCodes.Unauthorized, _, _, _) =>
      println("Shutting down with error : unauthorized")
      context.system.shutdown()
      System.exit(401)
    case HttpResponse(StatusCodes.EnhanceYourCalm, _ , _, _) =>
      println("Limit of connections exceeded")
      context.system.shutdown()
      System.exit(420)
    case other =>
      println(s"Unexpected receive $other")
      System.exit(500)
  }
}

// Processes queries responses
class TwitterQueryActor(query: String, aggregator: ActorRef) extends Actor {

  var last : String = ""

  def receive: Receive = {
    case ChunkedResponseStart(x) =>
      // do nothing
    case MessageChunk(entity, _) =>
      val chunk = new String(entity.toByteArray)
      val (tweets, lst) = scanResponseChunks((last + chunk).split("\r").filterNot((s) => s.isEmpty))
      last = lst.mkString("\r")
      tweets
        .foreach((tweet: Tweet) => aggregator ! (query, tweet))

    case ChunkedMessageEnd(_, _) =>
      // do nothing
    case other =>
      // All other messages forwarded to parent
      // to resolve possible problems
      context.parent.forward(other)
  }

  // Scan chunks to form Tweet objects
  // Assuming last string is incomplete by default
  def scanResponseChunks(seq: Array[String]): (Array[Tweet], Array[String]) = {
    if (seq.length < 2) {
      (Array(), seq)
    } else {
      // Can parse tweet unless it keep alive string
      val tweet: Array[Tweet] = if (seq.head.equals("\n")) Array() else Array(JsonParser(seq.head).convertTo[Tweet](TweetJsonProtocol.tweet))
      val (tweets, rest) = scanResponseChunks(seq.tail)
      (tweet ++ tweets, rest)
    }
  }
}
