package ai.nixiesearch.s3bench

import ai.nixiesearch.s3bench.core.{CliArgsParser, Logging, Measurement}
import ai.nixiesearch.s3bench.reader.Reader
import cats.effect
import cats.effect.{ExitCode, IO, IOApp}
import fs2.{Chunk, Stream}
import org.apache.commons.math3.stat.descriptive.rank.Percentile

import scala.concurrent.duration.*
import java.time.Instant
import scala.util.Random

object Main extends IOApp with Logging {
  override def run(args: List[String]): effect.IO[ExitCode] = for {
    args               <- CliArgsParser.load(args)
    _                  <- info(s"Args: ${args}")
    (reader, shutdown) <- Reader.make(args).allocated
    tasks              <- IO(0L.until(reader.size, reader.blockSize).toArray)
    _                  <- info("warmup...")
    _                  <- load(tasks = tasks, duration = 10.seconds, threads = 1, qd = args.qd, reader = reader)
//    _                  <- Stream
//      .iterate[IO, Int](0)(_ + 1)
//      .takeWhile(_ => Instant.now().isBefore(startTime.plusSeconds(10)))
//      .parEvalMapUnordered(args.threads)(idx => reader.read(idx = idx, offset = tasks(Random.nextInt(tasks.length))))
//      .groupWithin(chunkSize = Int.MaxValue, timeout = 1.second)
//      .evalTap(batch => logProgress(startTime, startTime.plusSeconds(10), batch.toList))
//      .unchunks
//      .compile
//      .toList
    _            <- info("Starting run")
    measurements <- load(tasks = tasks, duration = args.duration, threads = args.threads, reader = reader, qd = args.qd)
    _            <- info(s"Made ${measurements.size} reads")

  } yield {
    ExitCode.Success
  }

  def load(tasks: Array[Long], duration: FiniteDuration, threads: Int, qd: Int, reader: Reader): IO[List[Measurement]] =
    for {
      startTime    <- IO(Instant.now())
      endTime      <- IO(startTime.plusSeconds(duration.toSeconds))
      measurements <- Stream
        .repeatEval(IO(tasks(Random.nextInt(tasks.length))))
        .takeWhile(_ => Instant.now().isBefore(endTime))
        .chunkN(qd)
        .parEvalMapUnordered(threads)(chunk => reader.read(offsets = chunk.toArray).map(Chunk.from(_)))
        .unchunks
        .groupWithin(chunkSize = Int.MaxValue, timeout = 1.second)
        .evalTap(batch => logProgress(startTime, endTime, batch.toList))
        .unchunks
        .compile
        .toList
      _ <- logProgress(startTime, endTime, measurements)
    } yield {
      measurements
    }

  def logProgress(startTime: Instant, endTime: Instant, batch: List[Measurement]): IO[Unit] = {
    val period    = endTime.getEpochSecond - startTime.getEpochSecond
    val elapsed   = Instant.now().getEpochSecond - startTime.getEpochSecond
    val latencies = batch.map(m => (m.firstByte - m.start).toDouble)
    val perc      = new Percentile()
    perc.setData(latencies.toArray)

    val percValues = List(50, 90, 95, 99).map(p => { p -> perc.evaluate(p) }).map((p, v) => s"p${p}=${millis(v)}")
    info(
      s"[${100 * elapsed / period}%]: ${batch.size} rps, ${percValues.mkString(" ")}"
    )
  }

  def millis(nanos: Double): String = {
    (math.round(100 * nanos / 1000000) / 100.0f).toString
  }

  def report(results: List[Measurement]): IO[Unit] = IO {}
}
