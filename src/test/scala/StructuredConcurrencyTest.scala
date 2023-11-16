import Currency.USD
import ox.*

import scala.concurrent.duration.*
import scala.util.*

// For more information on writing tests, see
// https://scalameta.org/munit/docs/getting-started.html
enum Currency:
  case USD, EUR

class ExchangeRateService(val name: String, val latency: Long, val rates: Map[Currency, Double]) {
  def getExchangeRate(currency: Currency): Double = {
    println(s"${Thread.currentThread()} Fetching from $name...")
    Thread.sleep(latency)
    val result = rates.getOrElse(currency, 0.0)
    println(s"${Thread.currentThread()} Successfully fetched from $name!")
    result
  }
}

class StructuredConcurrencyTest extends munit.FunSuite {

  private val exchanges = Seq(
    ExchangeRateService("exchange 1", 1000, Map(USD -> 125.12)),
    ExchangeRateService("exchange 2 (fastest)", 500, Map(USD -> 126.2)),
    ExchangeRateService("exchange 3 (slowest)", 1500, Map(USD -> 124.12)),
  )

  test("should cancel slow exchanges") {
    println()
    println("Cancel slow exchanges test")
    println()
    val results = supervised {
      exchanges.map { ex =>
        fork {
          val currentThread = Thread.currentThread()
          val res = Try(timeout(800.millis)(ex.getExchangeRate(USD))) match
            case Success(res) =>
              println(s"$currentThread Task completed with result '$res")
              Some(res)
            case Failure(_) =>
              println(s"$currentThread Task cancelled!")
              None
          res
        }
      }.flatMap(_.join())
    }

    results.foreach(res => println(s"Got rate: $res"))

    assertEquals(results, Seq(126.2))
  }

  test("should get the result of the fastest exchange") {
    println()
    println("Race test")
    println()
    val winner = raceSuccess(exchanges.map { ex =>
      () => {
        val currentThread = Thread.currentThread()
        val res = ex.getExchangeRate(USD)
        println(s"$currentThread Task completed with result '$res")
        res
      }
    })
    println(s"Got rate: $winner")
    assertEquals(winner, 126.2)
  }
}
