import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config

object Main {
  def main (args: Array[String]) {
    println("Hello world")
    val config = ConfigFactory.load()
    println(config.getString("app-name") + " says " + config.getString("app-mod"))
  }
}
