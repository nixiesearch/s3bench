package ai.nixiesearch.s3bench.reader

import ai.nixiesearch.s3bench.core.{Logging, Measurement}
import cats.effect
import cats.effect.kernel.Resource

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{Files, Path, StandardOpenOption}
import cats.effect.IO
import com.sun.nio.file.ExtendedOpenOption

import scala.collection.mutable.ArrayBuffer

case class NIOReader(fc: FileChannel, blockSize: Int, size: Long, buffer: ByteBuffer) extends Reader {
  override def read(offsets: Array[Long]): IO[List[Measurement]] = IO {
    val result = ArrayBuffer[Measurement]()
    var i      = 0
    while (i < offsets.length) {
      val start = System.nanoTime()
      fc.read(buffer, offsets(i))
      buffer.flip()
      val end = System.nanoTime()
      i += 1
      result.addOne(Measurement(start, end, end, blockSize))
    }
    result.toList
  }
}

object NIOReader extends Logging {
  def create(path: Path, blockSize: Int): Resource[IO, NIOReader] =
    Resource.make[IO, NIOReader](IO(NIOReader.apply(path, blockSize)))(r => IO(r.fc.close()))

  def apply(path: Path, blockSize: Int) = {
    logger.info(s"NIOReader init: path=$path bs=$blockSize")
    new NIOReader(
      fc = FileChannel.open(path, StandardOpenOption.READ, ExtendedOpenOption.DIRECT),
      blockSize = blockSize,
      size = Files.size(path),
      buffer = ByteBuffer.allocateDirect(2 * blockSize + 1).alignedSlice(blockSize)
    )
  }
}
