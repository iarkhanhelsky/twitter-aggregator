package org.scala.lab.twitter

import com.typesafe.config.Config
import sun.misc.BASE64Encoder
import scala.collection.JavaConversions._

case class AkkaConf(systemName : String)
case class TwitterAuth(key : String, secret : String)
case class TwitterConfig(auth : TwitterAuth, queries : List[String])
case class AggregatorConfig(actors : AkkaConf, twitter : TwitterConfig)

object Configuration {
  def encode64(msg: String): String = {
    new BASE64Encoder().encode(msg.getBytes)
  }

  def readConfig(config: Config): AggregatorConfig = {
    return parse(check(config))
  }

  // Run some assertions on config
  // to check if valid parameters is set
  private def check(config : Config) : Config = {

    // If set to zero the individual response parts of chunked requests
    // are dispatched to the application as they come in.
    // Else we'll get response timeout
    assert(config.atPath("spray.client.response-chunk-aggregation-limit") == 0)

    return config
  }

  private def parse(config: Config): AggregatorConfig = {
    AggregatorConfig(
      AkkaConf(config.getString("actors.name")),
      TwitterConfig(
        TwitterAuth(
          config.getString("twitter.auth.key"),
          config.getString("twitter.auth.secret")),
        config.getStringList("queries").toList))
  }
}
