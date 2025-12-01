package ai.nixiesearch.s3bench.core

import java.nio.ByteBuffer


case class Measurement(start: Long, firstByte: Long, lastByte: Long, size: Long)
