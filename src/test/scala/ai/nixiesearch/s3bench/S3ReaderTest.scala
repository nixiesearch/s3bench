package ai.nixiesearch.s3bench

import ai.nixiesearch.s3bench.reader.S3Reader
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class S3ReaderTest extends AnyFlatSpec with Matchers {
  it should "send requests" in {
    val (reader, shutdown) = S3Reader
      .create(
        bucket = "nixiesearch-lambda-wiki",
        prefix = "config-local.yml",
        region = "us-east-1",
        size = 700
      )
      .allocated
      .unsafeRunSync()
    reader.read(Array(0L)).unsafeRunSync()
    shutdown.unsafeRunSync()
  }

  it should "list prefix" in {
    val (reader, shutdown) = S3Reader
      .create(
        bucket = "nixiesearch-lambda-wiki",
        prefix = "config-local.yml",
        region = "us-east-1",
        size = 700
      )
      .allocated
      .unsafeRunSync()
    val resp = reader.listObjects("config").unsafeRunSync()
    shutdown.unsafeRunSync()
  }
}
