import Currency.USD
import ox.*

import scala.concurrent.duration.*
import munit.*

import scala.concurrent.duration.Duration.fromNanos

// For more information on writing tests, see
// https://scalameta.org/munit/docs/getting-started.html
enum Currency:
  case USD, EUR

class ExchangeRateService(val name: String, val latency: Long, val rates: Map[Currency, Double]) {
  def getExchangeRate(currency: Currency): Double =
    println(s"${Thread.currentThread()} Fetching from $name...")
    Thread.sleep(latency)
    val result = rates.getOrElse(currency, 0.0)
    println(s"${Thread.currentThread()} Successfully fetched from $name!")
    result
}

class StructuredConcurrencyTest extends munit.FunSuite {

  private val exchanges = Seq(
    ExchangeRateService(name = "EXCH-1", latency = 1000, rates = Map(USD -> 125.12)),
    ExchangeRateService(name = "EXCH-2 (fastest)", latency = 500, rates = Map(USD -> 126.2)),
    ExchangeRateService(name = "EXCH-3 (slowest)", latency = 1500, rates = Map(USD -> 124.12)),
  )

  test("should get results concurrently") {
   val (elapsedTime, _) = measureTimeMillis {
      supervised {
        exchanges.map { exch =>
          fork {
            exch.getExchangeRate(USD)
          }
        }
      }
    }
    assert(elapsedTime < (exchanges.last.latency.millis + 300.millis), "Too slow!")
  }

  test("should cancel slow exchanges") {
    val results = supervised {
      exchanges.map { exch =>
        fork {
          val currentThread = Thread.currentThread()
          val res = timeoutOption(1100.millis)(exch.getExchangeRate(USD)) match
            case Some(res) =>
              println(s"$currentThread Task completed with result '$res")
              Some(res)
            case None =>
              println(s"$currentThread Task cancelled!")
              None
          res
        }
      }.flatMap(_.join())
    }

    results.foreach(res => println(s"Got rate: $res"))

    assertEquals(results, Seq(125.12, 126.2))
  }

  test("should get the result of the fastest exchange") {
    val (elapsedTime, winner) = measureTimeMillis {
      raceSuccess(exchanges.map { exch =>
        () => exch.getExchangeRate(USD)
      })
    }
    println(s"Got rate: $winner")
    assertEquals(winner, 126.2)
    assert(elapsedTime < (exchanges(1).latency.millis + 100.millis), "Too slow!")
  }
}

def measureTimeMillis[R](block: => R): (FiniteDuration, R) =
  val startTime = System.nanoTime()
  val result = block
  val endTime = System.nanoTime()
  (fromNanos(endTime - startTime), result)
