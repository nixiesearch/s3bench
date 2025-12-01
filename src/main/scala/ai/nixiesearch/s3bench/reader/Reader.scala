package ai.nixiesearch.s3bench.reader

import ai.nixiesearch.s3bench.core.CliArgsParser.CliArgs
import ai.nixiesearch.s3bench.core.{Logging, Measurement}
import cats.effect.IO
import cats.effect.kernel.Resource

trait Reader extends Logging {
  def size: Long
  def blockSize: Int
  def read(offsets: Array[Long]): IO[List[Measurement]]
}

object Reader {
  def make(args: CliArgs): Resource[IO, Reader] = args match {
    case s3: CliArgs.S3Args =>
      S3Reader.create(
        endpoint = s3.endpoint,
        bucket = s3.bucket,
        prefix = s3.prefix,
        region = s3.region,
        size = s3.size
      )
    case nio: CliArgs.NIOArgs => NIOReader.create(nio.path, nio.bs)
  }
}
