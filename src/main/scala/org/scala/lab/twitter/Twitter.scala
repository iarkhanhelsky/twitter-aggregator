package org.scala.lab.twitter

import akka.actor.{Props, ActorRef, Actor}
import akka.io.IO
import core.OAuth.{Token, Consumer}
import org.scala.lab.twitter.TwitterDefines._
import spray.can.Http
import spray.client.pipelining._
import spray.http._


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
}

// Base streaming actor which listens for query responses
class TweetStreamerActor(io : ActorRef) extends Actor {
  auth: TwitterAuthorization =>

  def receive: Receive = {

    case query: String =>
      val body = HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`), s"track=$query")
      val rq = HttpRequest(HttpMethods.POST, uri = twitterStreamingUri, entity = body) ~> authorize
      sendTo(io).withResponsesReceivedBy(context.actorOf(Props(new TwitterQueryActor)))(rq)

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
class TwitterQueryActor extends Actor {
  def receive: Receive = {
    case ChunkedResponseStart(_) =>
      // do nothing
    case MessageChunk(entity, _) =>
      println(new String(entity.toByteString.toArray))
    case other =>
      // All other messages forwarded to parent
      // to resolve possible problems
      context.parent.forward(other)
  }
}
