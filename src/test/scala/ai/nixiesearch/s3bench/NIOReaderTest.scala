package ai.nixiesearch.s3bench

import ai.nixiesearch.s3bench.reader.NIOReader
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import java.nio.file.Files

class NIOReaderTest extends AnyFlatSpec with Matchers {
  it should "read data" in {
    val path   = Files.createTempFile("temp", ".bin")
    val file   = Files.write(path, List.fill(1024)("0123456789").mkString("").getBytes)
    val reader = NIOReader(path, 4096)
    val result = reader.read(Array(0L)).unsafeRunSync()
  }
}
