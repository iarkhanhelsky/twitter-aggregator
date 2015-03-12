package org.scala.lab.twitter

import akka.actor.{ActorRef, Actor}
import org.scala.lab.twitter.TwitterDefines.Tweet

/**
 * Created by Ilya Arkhanhelsky on 3/12/15.
 */

// Print each tweet formatted
class PrintAggregator extends Actor {
  override def receive: Receive = {
    case (query: String, tweet : Tweet) =>
      println(s"query: %${query.length}s :: @%-20s >  %s".format(query, tweet.user.username, tweet.text))
    case unexpected =>
    // TODO: Do something with unexpected message
    // TODO: Or not?
  }
}

// Count query hits and plot histogram bars on stop
class HistogramAggregator extends Actor {

  var factorCountMap = Map[String, Int]()

  override def receive: Actor.Receive = {
    case (query: String, _: Tweet) =>
      val hits = if(factorCountMap.contains(query)) factorCountMap(query) else 0

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

// Join multiple actors
// TODO: Already implemented? 
class AggregatorList(actors: ActorRef*) extends Actor {
  override def receive: Actor.Receive = {
    case message =>
      actors.foreach( _ ! message)
  }
}
