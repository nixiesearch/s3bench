package ai.nixiesearch.s3bench

import cats.effect.IO
import fs2.{Pipe, Pull, Stream}

object MeasureFirstLast {
  case class Timing(first: Long, last: Long)

  def measureFirstLast[T](): Pipe[IO, T, Timing] =
    in => measureFirstLastRec(in, None).stream

  private def measureFirstLastRec[T](s: Stream[IO, T], first: Option[Long]): Pull[IO, Timing, Unit] =
    s.pull.uncons1.flatMap {
      case None =>
        first match {
          // empty input
          case None => Pull.done
          // last item
          case Some(f) => Pull.output1(Timing(f, System.nanoTime())) >> Pull.done
        }
      case Some((head, tail)) =>
        first match {
          // first item
          case None => measureFirstLastRec(tail, first = Some(System.nanoTime()))
          // middle item
          case Some(_) => measureFirstLastRec(tail, first = first)
        }
    }

}
