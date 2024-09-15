package ai.nixiesearch.s3bench

import ai.nixiesearch.s3bench.CliArgsParser.CliArgs
import cats.effect.{ExitCode, IO, IOApp}
import fs2.Stream

object Main extends IOApp with Logging {
  case class Sample(start: Long, end: Long, request: Long, first: Long)

  override def run(args: List[String]): IO[ExitCode] = for {
    config <- CliArgsParser.load(args)
    _      <- info(s"starting job ${config}")
    samples <- S3Client
      .create(config.region, config.endpoint)
      .use(client =>
        Stream
          .range[IO, Int](0, config.requests)
          .parEvalMapUnordered(config.threads)(_ => sample(config, client))
          .through(PrintProgress.tap("requests"))
          .evalTap(sample =>
            IO.whenA(config.verbose)(
              info(
                s"read: request=${sample.request - sample.start} first=${sample.first - sample.start} last=${sample.end - sample.start}"
              )
            )
          )
          .compile
          .toList
      )
    stats <- IO(Stats(samples, config.percentiles))
    _     <- info("\n" + stats.print())
  } yield {
    ExitCode.Success
  }

  def sample(config: CliArgs, client: S3Client): IO[Sample] = for {
    start   <- IO(System.nanoTime())
    stream  <- client.getObject(config.bucket, config.prefix)
    request <- IO(System.nanoTime())
    timing <- stream.through(MeasureFirstLast.measureFirstLast()).compile.last.flatMap {
      case None       => IO.raiseError(new Exception("no bytes read"))
      case Some(last) => IO.pure(last)
    }
    end <- IO(System.nanoTime())
  } yield {
    Sample(start = start, end = end, first = timing.first, request = request)
  }

}
