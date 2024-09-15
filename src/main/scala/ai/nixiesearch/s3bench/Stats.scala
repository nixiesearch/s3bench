package ai.nixiesearch.s3bench

import ai.nixiesearch.s3bench.Main.Sample

case class Stats(stats: Map[String, List[(Int, Double)]]) {
  def print(): String = {
    stats
      .map { case (name, percentiles) =>
        val percString = percentiles.sortBy(_._1).map { case (perc, value) =>
          s"$perc=$value"
        }
        s"$name: ${percString.mkString(" ")}"
      }
      .mkString("\n")
  }
}

object Stats {
  case class Percentile(internal: org.apache.commons.math3.stat.descriptive.rank.Percentile) {
    def values(perc: List[Int]): List[(Int, Double)] =
      perc.map(p => p -> internal.evaluate(p.toDouble))
  }

  object Percentile {
    def apply(values: List[Double]) = {
      val p = new org.apache.commons.math3.stat.descriptive.rank.Percentile()
      p.setData(values.toArray)
      new Percentile(p)
    }
  }
  def apply(samples: List[Sample], percentiles: List[Int]): Stats = {
    val request = samples.map(s => (s.request - s.start) / 1000000.0)
    val first   = samples.map(s => (s.first - s.start) / 1000000.0)
    val last    = samples.map(s => (s.end - s.start) / 1000000.0)
    val stats = Map(
      "request" -> Percentile(request).values(percentiles),
      "first"   -> Percentile(first).values(percentiles),
      "total"   -> Percentile(last).values(percentiles)
    )
    Stats(stats)
  }

}
