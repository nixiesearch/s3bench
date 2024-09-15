package ai.nixiesearch.s3bench

import ai.nixiesearch.s3bench.S3Client.S3GetObjectResponseStream
import cats.effect.{IO, Resource}
import software.amazon.awssdk.auth.credentials.internal.LazyAwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.{AnonymousCredentialsProvider, AwsCredentialsProvider, AwsCredentialsProviderChain, DefaultCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import fs2.{Chunk, Stream}
import software.amazon.awssdk.core.async.{AsyncResponseTransformer, SdkPublisher}
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse}
import fs2.interop.reactivestreams.fromPublisher

import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import scala.compiletime.uninitialized

case class S3Client(client: S3AsyncClient) {
  def getObject(bucket: String, path: String): IO[Stream[IO, Byte]] = for {
    request <- IO(GetObjectRequest.builder().bucket(bucket).key(path).build())
    stream  <- IO.fromCompletableFuture(IO(client.getObject(request, S3GetObjectResponseStream())))
  } yield {
    stream
  }
}

object S3Client {
  class S3GetObjectResponseStream[T]()
      extends AsyncResponseTransformer[GetObjectResponse, Stream[IO, Byte]]
      with Logging {
    var cf: CompletableFuture[Stream[IO, Byte]] = uninitialized

    override def prepare(): CompletableFuture[Stream[IO, Byte]] = {
      cf = new CompletableFuture[Stream[IO, Byte]]()
      cf
    }

    override def onResponse(response: GetObjectResponse): Unit = {
      logger.debug(s"S3 response: $response")
    }

    override def onStream(publisher: SdkPublisher[ByteBuffer]): Unit = {
      logger.debug("subscribed to S3 GetObject data stream")
      val stream = fromPublisher[IO, ByteBuffer](publisher, 1).flatMap(bb => Stream.chunk(Chunk.byteBuffer(bb)))
      cf.complete(stream)
    }

    override def exceptionOccurred(error: Throwable): Unit = {
      cf.completeExceptionally(error)
    }
  }

  def create(region: String, endpoint: Option[String]): Resource[IO, S3Client] = for {
    creds <- Resource.eval(IO(createCredentialsProvider()))
    clientBuilder <- Resource.eval(
      IO(
        S3AsyncClient
          .builder()
          .region(Region.of(region))
          .credentialsProvider(creds)
          .forcePathStyle(endpoint.isDefined)
      )
    )
    client <- endpoint match {
      case Some(endpoint) =>
        Resource.make(IO(clientBuilder.endpointOverride(URI.create(endpoint)).build()))(c => IO(c.close()))
      case None => Resource.make(IO(clientBuilder.build()))(c => IO(c.close()))
    }

  } yield {
    S3Client(client)
  }

  def createCredentialsProvider(): AwsCredentialsProvider = {
    val chain = AwsCredentialsProviderChain
      .builder()
      .addCredentialsProvider(DefaultCredentialsProvider.create())
      .addCredentialsProvider(AnonymousCredentialsProvider.create())
      .build()
    LazyAwsCredentialsProvider.create(() => chain)
  }
}
