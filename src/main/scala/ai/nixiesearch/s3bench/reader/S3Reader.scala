package ai.nixiesearch.s3bench.reader

import ai.nixiesearch.s3bench.core.{Logging, Measurement}
import ai.nixiesearch.s3bench.reader.S3Reader.{CommonPrefix, ListObjectsV2Response, S3Object}
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.http4s.client.Client
import cats.effect.{IO, Resource}
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.ci.CIString
import cats.syntax.all.*

import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.xml.XML

case class S3Reader(
    client: Client[IO],
    endpoint: Uri,
    key: String,
    secret: String,
    blockSize: Int,
    size: Long,
    prefix: String,
    region: String
) extends Reader {
  val service                                                    = "s3"
  override def read(offsets: Array[Long]): IO[List[Measurement]] =
    offsets.toList.traverse(readOne)

  def readOne(offset: Long): IO[Measurement] = for {
    start <- IO(System.nanoTime())
    // Cap the range end at the object size to avoid requesting beyond EOF
    rangeEnd = Math.min(offset + blockSize - 1, size - 1)
    range    = s"bytes=$offset-$rangeEnd"
    request <- signRequest(endpoint / prefix, extraHeaders = Map("range" -> range))
    content <- client.stream(request).evalMap(_.entity.body.compile.to(Array)).compile.toList
    end     <- IO(System.nanoTime())
    // Calculate actual bytes read
    actualBytesRead = content.map(_.length).sum
  } yield {
    Measurement(start, end, end, actualBytesRead)
  }

  def listObjects(
      prefix: String,
      maxKeys: Int = 1000,
      continuationToken: Option[String] = None
  ): IO[ListObjectsV2Response] = {
    val baseParams = Map(
      "list-type" -> "2",
      "prefix"    -> prefix,
      "max-keys"  -> maxKeys.toString
    )
    val queryParams = continuationToken match {
      case Some(token) => baseParams + ("continuation-token" -> token)
      case None        => baseParams
    }

    for {
      request  <- signRequest(endpoint, queryParams = queryParams)
      response <- client
        .stream(request)
        .evalMap { resp =>
          resp.entity.body.compile.to(Array).map(bytes => new String(bytes, StandardCharsets.UTF_8))
        }
        .compile
        .toList
      parsed <- IO(parseListObjectsV2Response(response.mkString))
    } yield parsed
  }

  private def parseListObjectsV2Response(xmlString: String): ListObjectsV2Response = {
    val xml = XML.loadString(xmlString)

    val contents = (xml \ "Contents").map { content =>
      S3Object(
        key = (content \ "Key").text,
        size = (content \ "Size").text.toLong,
        lastModified = (content \ "LastModified").text,
        eTag = (content \ "ETag").text,
        storageClass = (content \ "StorageClass").headOption.map(_.text)
      )
    }.toList

    val commonPrefixes = (xml \ "CommonPrefixes").map { cp =>
      CommonPrefix(prefix = (cp \ "Prefix").text)
    }.toList

    ListObjectsV2Response(
      contents = contents,
      commonPrefixes = commonPrefixes,
      isTruncated = (xml \ "IsTruncated").text.toBoolean,
      keyCount = (xml \ "KeyCount").text.toInt,
      name = (xml \ "Name").text,
      prefix = (xml \ "Prefix").text,
      maxKeys = (xml \ "MaxKeys").text.toInt,
      continuationToken = (xml \ "ContinuationToken").headOption.map(_.text),
      nextContinuationToken = (xml \ "NextContinuationToken").headOption.map(_.text)
    )
  }

  def signRequest(
      uri: Uri,
      queryParams: Map[String, String] = Map.empty,
      extraHeaders: Map[String, String] = Map.empty
  ): IO[Request[IO]] = IO {
    // Step 1: Generate timestamp
    val now       = ZonedDateTime.now(ZoneOffset.UTC)
    val dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    val amzDate   = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))

    // Step 2: Build final URI with query parameters first
    val finalUri = queryParams.foldLeft(uri) { case (u, (k, v)) => u.withQueryParam(k, v) }

    // Step 3: Build canonical query string (sorted, URL-encoded)
    // We need to manually encode because we need the exact encoded form for signing
    val canonicalQueryString = queryParams.toList
      .sortBy(_._1)
      .map { case (k, v) => s"${urlEncode(k)}=${urlEncode(v)}" }
      .mkString("&")

    // Step 4: Build canonical headers (sorted, include host, x-amz-*, and extra headers)
    val host        = uri.host.get.value
    val payloadHash = sha256Hex("")

    val baseHeaders = Map(
      "host"                 -> host,
      "x-amz-content-sha256" -> payloadHash,
      "x-amz-date"           -> amzDate
    )

    val allHeaders       = baseHeaders ++ extraHeaders
    val sortedHeaderKeys = allHeaders.keys.toList.sorted
    val canonicalHeaders = sortedHeaderKeys.map(k => s"$k:${allHeaders(k)}").mkString("\n") + "\n"
    val signedHeaders    = sortedHeaderKeys.mkString(";")

    // Step 5: Create canonical request
    val method           = "GET"
    val canonicalUri     = if (uri.path.isEmpty || uri.path.toString.isEmpty) "/" else uri.path.toString
    val canonicalRequest =
      s"$method\n$canonicalUri\n$canonicalQueryString\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

    // Step 6: Create string to sign
    val algorithm       = "AWS4-HMAC-SHA256"
    val credentialScope = s"$dateStamp/$region/$service/aws4_request"
    val stringToSign    = s"$algorithm\n$amzDate\n$credentialScope\n${sha256Hex(canonicalRequest)}"

    // Step 7: Calculate signing key
    val kDate    = hmacSha256(s"AWS4$secret".getBytes(StandardCharsets.UTF_8), dateStamp)
    val kRegion  = hmacSha256(kDate, region)
    val kService = hmacSha256(kRegion, service)
    val kSigning = hmacSha256(kService, "aws4_request")

    // Step 8: Calculate signature
    val signature = hmacSha256Hex(kSigning, stringToSign)

    // Step 9: Build Authorization header
    val authorizationHeader =
      s"$algorithm Credential=$key/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

    // Step 10: Build request headers
    val requestHeaders = List(
      Header.Raw(CIString("Host"), host),
      Header.Raw(CIString("x-amz-date"), amzDate),
      Header.Raw(CIString("x-amz-content-sha256"), payloadHash),
      Header.Raw(CIString("Authorization"), authorizationHeader)
    ) ++ extraHeaders.map { case (k, v) => Header.Raw(CIString(k), v) }

    // Step 11: Create and return the request
    Request[IO](
      method = Method.GET,
      uri = finalUri,
      headers = Headers(requestHeaders)
    )
  }

  private def sha256Hex(data: String): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash   = digest.digest(data.getBytes(StandardCharsets.UTF_8))
    hash.map("%02x".format(_)).mkString
  }

  private def hmacSha256(key: Array[Byte], data: String): Array[Byte] = {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(key, "HmacSHA256"))
    mac.doFinal(data.getBytes(StandardCharsets.UTF_8))
  }

  private def hmacSha256Hex(key: Array[Byte], data: String): String = {
    val hash = hmacSha256(key, data)
    hash.map("%02x".format(_)).mkString
  }

  private def urlEncode(value: String): String = {
    // AWS SigV4 requires RFC 3986 encoding (not application/x-www-form-urlencoded)
    // URLEncoder encodes spaces as '+' but we need '%20'
    java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
  }
}

object S3Reader extends Logging {

  case class S3Object(
      key: String,
      size: Long,
      lastModified: String,
      eTag: String,
      storageClass: Option[String] = None
  )

  case class CommonPrefix(
      prefix: String
  )

  case class ListObjectsV2Response(
      contents: List[S3Object],
      commonPrefixes: List[CommonPrefix],
      isTruncated: Boolean,
      keyCount: Int,
      name: String,
      prefix: String,
      maxKeys: Int,
      continuationToken: Option[String],
      nextContinuationToken: Option[String]
  )

  def create(bucket: String, prefix: String, region: String, size: Int, endpoint: Option[String] = None) = {
    val realEndpoint = endpoint.getOrElse(s"https://$bucket.s3.$region.amazonaws.com")
    for {

      client   <- EmberClientBuilder.default[IO].build
      endpoint <- Resource.eval(IO.fromEither(Uri.fromString(realEndpoint)))
      key      <- Resource.eval(
        IO.fromOption(Option(System.getenv("AWS_ACCESS_KEY_ID")))(new Exception("AWS_ACCESS_KEY_ID env is missing"))
      )
      secret <- Resource.eval(
        IO.fromOption(Option(System.getenv("AWS_SECRET_ACCESS_KEY")))(
          new Exception("AWS_SECRET_ACCESS_KEY env is missing")
        )
      )
    } yield {
      S3Reader(
        client = client,
        endpoint = endpoint,
        key = key,
        secret = secret,
        blockSize = 4096,
        size = size,
        prefix = prefix,
        region = region
      )
    }
  }
}
