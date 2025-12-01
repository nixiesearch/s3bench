package ai.nixiesearch.s3bench

import ai.nixiesearch.s3bench.core.CliArgsParser
import ai.nixiesearch.s3bench.core.CliArgsParser.CliArgs.NIOArgs
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import java.nio.file.Paths
import scala.concurrent.duration.*

class CliArgsParserTest extends AnyFlatSpec with Matchers {
  it should "parse minimal nio args" in {
    val result = CliArgsParser.load(List("nio", "--path", "/tmp/file.txt")).unsafeRunSync()
    result shouldBe NIOArgs(path = Paths.get("/tmp/file.txt"))
  }
  it should "parse full nio args" in {
    val result =
      CliArgsParser.load(List("nio", "--path", "/tmp/file.txt", "--bs", "1024", "--duration", "1s")).unsafeRunSync()
    result shouldBe NIOArgs(path = Paths.get("/tmp/file.txt"), bs = 1024, duration = 1.second)
  }
}
