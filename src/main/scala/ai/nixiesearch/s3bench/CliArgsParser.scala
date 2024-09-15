package ai.nixiesearch.s3bench

import ai.nixiesearch.s3bench.CliArgsParser.IntListConverter
import cats.effect.IO
import org.rogach.scallop.ScallopConf
import org.rogach.scallop.exceptions.{Help, ScallopException, ScallopResult, Version as ScallopVersion}
import org.rogach.scallop.{ScallopConf, ScallopOption, Subcommand, throwError, given}

import scala.util.{Failure, Success, Try}

case class CliArgsParser(arguments: List[String]) extends ScallopConf(arguments) with Logging {
  val bucket = opt[String](name = "bucket", descr = "S3 bucket (required)", required = true)
  val prefix = opt[String](name = "prefix", descr = "S3 prefix (required)", required = true)
  val requests =
    opt[Int](
      name = "requests",
      descr = "how many requests to perform (optional, default 100)",
      required = false,
      default = Some(100)
    )

  val endpoint = opt[String](name = "endpoint", descr = "S3 endpoint URL (optional)", required = false)
  val threads =
    opt[Int](
      name = "threads",
      descr = "How many parallel clients to spawn (optional, default 1)",
      required = false,
      default = Some(1)
    )
  val region = opt[String](
    name = "region",
    descr = "AWS region (optional, default us-east-1)",
    required = false,
    default = Some("us-east-1")
  )
  val verbose = opt[Boolean](name = "verbose", descr = "enable extra prints", required = false, default = Some(false))
  val percentiles = opt[List[Int]](
    name = "percentiles",
    descr = "which stats to compute",
    required = false,
    default = Some(List(50, 90, 95, 99))
  )(IntListConverter)

  banner("""Usage: s3bench  <options>
           |Options:
           |""".stripMargin)
  footer("\nFor all other tricks, consult the docs on https://github.com/nixiesearch/s3bench")

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

object CliArgsParser {
  case class CliArgs(
      bucket: String,
      prefix: String,
      requests: Int,
      endpoint: Option[String],
      threads: Int,
      region: String,
      verbose: Boolean,
      percentiles: List[Int]
  )

  object IntListConverter extends ArgConverter[List[Int]] {
    override def convert(value: String): Either[String, List[Int]] =
      value.split(",").toList.foldLeft[Either[String, List[Int]]](Right(Nil)) {
        case (Left(err), _) => Left(err)
        case (Right(list), next) =>
          next.toIntOption match {
            case Some(intValue) => Right(intValue +: list)
            case None           => (Left(s"cannot convert $next to int value"))
          }
      }
  }

  def load(args: List[String]): IO[CliArgs] = for {
    parser      <- IO(CliArgsParser(args))
    _           <- IO(parser.verify())
    bucket      <- parse(parser.bucket)
    prefix      <- parse(parser.prefix)
    requests    <- parse(parser.requests)
    endpoint    <- parseOption(parser.endpoint)
    threads     <- parse(parser.threads)
    region      <- parse(parser.region)
    verbose     <- parse(parser.verbose)
    percentiles <- parse(parser.percentiles)
  } yield {
    CliArgs(bucket, prefix, requests, endpoint, threads, region, verbose, percentiles)
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
