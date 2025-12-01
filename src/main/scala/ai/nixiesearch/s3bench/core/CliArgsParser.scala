package ai.nixiesearch.s3bench.core

import ai.nixiesearch.s3bench.core.CliArgsParser.CliArgs.{NIOArgs, S3Args}
import org.rogach.scallop.exceptions.{Help, ScallopException, ScallopResult, Version as ScallopVersion}
import org.rogach.scallop.{ScallopConf, ScallopOption, Subcommand, throwError, given}
import cats.effect.IO
import scala.concurrent.duration.*

import java.nio.file.Path
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

case class CliArgsParser(arguments: List[String]) extends ScallopConf(arguments) with Logging {
  trait DefaultParams { this: Subcommand =>
    val bs       = opt[Int](name = "bs", required = false, default = Some(4096))
    val qd       = opt[Int](name = "qd", required = false, default = Some(64))
    val threads  = opt[Int](name = "threads", required = false, default = Some(1))
    val duration = opt[FiniteDuration](name = "duration", required = false, default = Some(1.minute))
  }
  object s3 extends Subcommand("s3") with DefaultParams {
    val bucket   = opt[String](name = "bucket", required = true, descr = "bucket name")
    val prefix   = opt[String](name = "prefix", required = true, descr = "prefix")
    val endpoint = opt[String](name = "endpoint", required = false, descr = "endpoint", default = None)
    val region   = opt[String](name = "region", required = true, descr = "aws region")
    val size     = opt[Int](name = "size", required = true)
  }
  addSubcommand(s3)

  object nio extends Subcommand("nio") with DefaultParams {
    val path = opt[Path](name = "path", required = true)
  }
  addSubcommand(nio)

  banner("S3/EFS/EBS benchmark tool.")

  override protected def onError(e: Throwable): Unit = e match {
    case r: ScallopResult if !throwError.value =>
      r match {
        case Help("") =>
          logger.info("\n" + builder.getFullHelpString())
        case Help(subname) =>
          logger.info("\n" + builder.findSubbuilder(subname).get.getFullHelpString())
        case ScallopVersion =>
          "\n" + getVersionString().foreach(logger.info)
        case e @ ScallopException(message) => throw e
        // following should never match, but just in case
        case other: ScallopException => throw other
      }
    case e => throw e
  }
}

object CliArgsParser extends Logging {
  sealed trait CliArgs {
    def qd: Int
    def bs: Int
    def threads: Int
    def duration: FiniteDuration
  }

  object CliArgs {
    case class S3Args(
        bucket: String,
        prefix: String,
        region: String,
        size: Int,
        qd: Int = 1,
        bs: Int = 4096,
        threads: Int = 1,
        endpoint: Option[String] = None,
        duration: FiniteDuration = 1.minute
    ) extends CliArgs
    case class NIOArgs(path: Path, qd: Int = 64, bs: Int = 4096, threads: Int = 1, duration: FiniteDuration = 1.minute)
        extends CliArgs
  }

  def load(args: List[String]): IO[CliArgs] = for {
    parser <- IO(CliArgsParser(args))
    _      <- IO(parser.verify())
    opts   <- parser.subcommand match {
      case Some(parser.s3) =>
        for {
          bucket   <- parse(parser.s3.bucket)
          prefix   <- parse(parser.s3.prefix)
          bs       <- parse(parser.s3.bs)
          endpoint <- parseOption(parser.s3.endpoint)
          threads  <- parse(parser.s3.threads)
          duration <- parse(parser.s3.duration)
          region   <- parse(parser.s3.region)
          size     <- parse(parser.s3.size)
          qd       <- parse(parser.s3.qd)
        } yield {
          S3Args(
            bucket = bucket,
            prefix = prefix,
            bs = bs,
            endpoint = endpoint,
            threads = threads,
            duration = duration,
            region = region,
            size = size,
            qd = qd
          )
        }
      case Some(parser.nio) =>
        for {
          path     <- parse(parser.nio.path)
          bs       <- parse(parser.nio.bs)
          threads  <- parse(parser.nio.threads)
          duration <- parse(parser.nio.duration)
          qd       <- parse(parser.nio.qd)
        } yield {
          NIOArgs(path = path, qd = qd, bs = bs, threads = threads, duration = duration)
        }
      case Some(other) =>
        IO.raiseError(new Exception(s"Subcommand $other is not supported."))
      case None => IO.raiseError(new Exception("No command given."))

    }
  } yield {
    opts
  }

  def parse[T](option: ScallopOption[T]): IO[T] = {
    Try(option.toOption) match {
      case Success(Some(value)) => IO.pure(value)
      case Success(None)        => IO.raiseError(new Exception(s"missing required option ${option.name}"))
      case Failure(ex)          => IO.raiseError(ex)
    }
  }

  def parseOption[T](option: ScallopOption[T]): IO[Option[T]] = {
    Try(option.toOption) match {
      case Success(value) => IO.pure(value)
      case Failure(ex)    => IO.raiseError(ex)
    }
  }

}
