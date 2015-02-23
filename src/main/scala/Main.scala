import akka.actor.{Props, ActorSystem, Actor}
import akka.actor.Actor.Receive
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config

import scala.util.Random

class HelloActor extends Actor {
  override def receive = {
    case "hello" => println("Mello")
    case 10 => "Ten"
    case _ => println("Wello")
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    super.postStop()
    println("Bye")
  }
}

object Main {
  def main (args: Array[String]) {
    val system = ActorSystem.create("hellosystem")
    val actor = system.actorOf(Props[HelloActor], "helloactor")
    actor ! "hello"
    actor ! "mello"
    Stream.continually(Random.nextInt(10)).map(actor ! _)
  }
}
