package org.scala.lab.twitter

import akka.actor.{Props, ActorRef, Actor}
import org.scala.lab.twitter.TwitterDefines._
import spray.http.Uri.Query
import spray.http._
import spray.json._
import spray.client.pipelining._

import scala.collection.mutable

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
class TweetStreamerActor(io : ActorRef) extends Actor {
  auth: TwitterAuthorization =>

  def receive: Receive = {

    case query: String =>
      val body = HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`), s"track=$query")
      val rq = HttpRequest(HttpMethods.POST, uri = twitterStreamingUri, entity = body) ~> authorize
      sendTo(io).withResponsesReceivedBy(context.actorOf(Props(new TwitterQueryActor(query))))(rq)

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
class TwitterQueryActor(query: String) extends Actor {
  import TweetJsonProtocol.tweet
  import TweetJsonProtocol.user

  var last : String = ""

  def receive: Receive = {
    case ChunkedResponseStart(x) =>
      // do nothing
    case MessageChunk(entity, _) =>
      val chunk = new String(entity.toByteArray)
      val (tweets, lst) = scan((last + chunk).split("\r").filterNot(_.isEmpty))
      last = lst.mkString("\r")
      tweets
        .map((t : Tweet) => (t.user.username, t.text))
        .map((v : (String, String)) => s"$query :: @${v._1} > ${v._2}")
        .foreach(println)

    case ChunkedMessageEnd(_, _) =>
      // do nothing
    case other =>
      // All other messages forwarded to parent
      // to resolve possible problems
      context.parent.forward(other)
  }

  def scan(seq: Array[String]): (Array[Tweet], Array[String]) = {
    if (seq.length < 2) {
      (Array(), seq)
    } else {
      val tweet = JsonParser(seq.head).convertTo[Tweet](TweetJsonProtocol.tweet)
      val (tweets, rest) = scan(seq.tail)
      (Array(tweet) ++ tweets, rest)
    }
  }
}
