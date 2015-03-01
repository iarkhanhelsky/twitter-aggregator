package org.scala.lab.twitter

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import org.scala.lab.twitter.Configuration._

object TwiAgg {
  def main(args: Array[String]): Unit = {
    // Read config
    val config = readConfig(
      ConfigFactory.load("application.json").withFallback(ConfigFactory.load("auth.json"))) // fallback to stubbed file
                                                                                            // which not commited

    implicit val actors = ActorSystem(config.actors.systemName)

    val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

    val response: Future[HttpResponse] = pipeline(Post("https://api.twitter.com/oauth2/token",
      Authorization(BasicHttpCredentials(Tools.encode64(config.twitter.auth.key + ":" + config.twitter.auth.secret))),
      HttpEntity(MediaTypes.`application/x-www-form-urlencoded`, "grant_type=client_credentials")))

    response onComplete {
      case Success(somethingUnexpected) =>
        println("Ok with something: " + somethingUnexpected)

      case Failure(error) =>
        println("Fail")
    }
  }
}
