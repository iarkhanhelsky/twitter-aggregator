package org.scala.lab.twitter

import akka.actor.Actor
import akka.io.IO
import core.OAuth.{Token, Consumer}
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
class TweetStreamerActor(uri: Uri, consumer: Consumer, token: Token) extends Actor {
  this: TwitterAuthorization =>
  val io = IO(Http)(context.system)

  def receive: Receive = {

    case query: String =>
      val body = HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`), s"track=$query")
      val rq = HttpRequest(HttpMethods.POST, uri = uri, entity = body) ~> authorize
      sendTo(io).withResponsesReceivedBy(self)(rq)
    case ChunkedResponseStart(_) =>

    case MessageChunk(entity, _) => println(new String(entity.toByteString.toArray))

    case HttpResponse(StatusCodes.Unauthorized, _, _, _) =>
      println("Shutting down with error : unauthorized")
      context.system.shutdown()
      System.exit(401)

    case other =>
      println(s"Unexpected receive $other")
      System.exit(500)
  }
}
