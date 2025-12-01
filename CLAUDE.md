# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

s3bench2 is a benchmarking tool written in Scala 3.7.4 using functional programming with Cats Effect. It measures I/O performance for both local file systems (via NIO with direct I/O) and S3-compatible storage backends.

## Build System

This project uses **SBT (Scala Build Tool)**.

### Common Commands

- **Compile the project**: `sbt compile`
- **Run tests**: `sbt test`
- **Run a single test**: `sbt "testOnly ai.nixiesearch.s3bench.NIOReaderTest"`
- **Run the application**: `sbt run`
- **Start SBT interactive shell**: `sbt` (then use commands like `compile`, `test`, `run`)
- **Clean build artifacts**: `sbt clean`

### Running the Application

The application supports two modes via subcommands:

**NIO mode** (local file system with direct I/O):
```bash
sbt "run nio --path /path/to/file --bs 4096 --threads 8 --duration 60s"
```

**S3 mode** (S3-compatible storage):
```bash
export AWS_ACCESS_KEY_ID=your_key
export AWS_SECRET_ACCESS_KEY=your_secret
sbt "run s3 --bucket mybucket --prefix path/to/object --endpoint https://s3.amazonaws.com --bs 4096 --threads 8 --duration 60s"
```

## Architecture

### Core Components

1. **Reader abstraction** (`ai.nixiesearch.s3bench.reader.Reader`):
   - Trait defining the interface for reading data with performance measurements
   - Methods: `read(offset: Long): IO[Measurement]`, `size: Long`, `blockSize: Int`
   - Factory in companion object pattern-matches `CliArgs` to create appropriate reader implementation

2. **NIOReader** (`ai.nixiesearch.s3bench.reader.NIOReader`):
   - Uses Java NIO FileChannel for direct I/O operations
   - Allocates aligned direct ByteBuffers for performance: `ByteBuffer.allocateDirect(2 * blockSize + 1).alignedSlice(blockSize)`
   - Uses `ExtendedOpenOption.DIRECT` to bypass OS page cache for accurate measurements
   - Managed as a `Resource[IO, NIOReader]` with proper cleanup

3. **S3Reader** (`ai.nixiesearch.s3bench.reader.S3Reader`):
   - Uses http4s Ember client for S3 HTTP requests
   - Implements AWS Signature Version 4 signing for request authentication
   - Reads AWS credentials from environment variables: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
   - Currently hardcoded to `us-east-1` region
   - Uses HMAC-SHA256 for request signing

4. **Measurement** (`ai.nixiesearch.s3bench.core.Measurement`):
   - Captures timing data: `start`, `firstByte`, `lastByte` (nanoseconds via `System.nanoTime()`)
   - Stores payload `size`
   - Note: `firstByte` and `lastByte` currently have same value in both implementations

5. **CliArgsParser** (`ai.nixiesearch.s3bench.core.CliArgsParser`):
   - Uses Scallop library for CLI parsing with subcommand pattern
   - Two ADT cases: `S3Args` and `NIOArgs`, both extending `CliArgs` trait
   - Common parameters: `bs` (block size, default 4096), `threads` (default 1), `duration` (default 1 minute)
   - Custom error handling that logs help messages instead of throwing

6. **Main** (`ai.nixiesearch.s3bench.Main`):
   - Extends `IOApp` from Cats Effect
   - Benchmark loop: generates random offsets within file, runs parallel reads for specified duration
   - Uses FS2 `Stream.repeatEval` + `parEvalMapUnordered` for concurrent read execution
   - Stops based on time duration using `Instant.now().isBefore(endTime)`

### Key Dependencies

- **Cats Effect 3.6.3**: Effect system for managing side effects
- **FS2 3.12.2**: Functional streaming library (core and I/O)
- **http4s 1.0.0-M46**: HTTP client using Ember (for S3 operations)
- **Circe 0.14.15**: JSON parsing/encoding
- **Logback 1.5.21 + Log4Cats 2.7.1**: Logging infrastructure
- **Scallop 5.3.0**: Command-line argument parsing
- **ScalaTest 3.2.19**: Testing framework

### Important Technical Details

- **Direct I/O**: NIOReader uses `ExtendedOpenOption.DIRECT` to bypass OS page cache for accurate performance measurements
- **ByteBuffer alignment**: Buffers are aligned to block size boundaries for optimal performance
- **Resource management**: Both readers use Cats Effect `Resource` for safe cleanup
- **Concurrency**: Benchmark uses `parEvalMapUnordered` to run multiple reads in parallel, with configurable thread count
- **AWS SigV4**: S3Reader implements full AWS Signature Version 4 request signing
- **Functional streaming**: Main benchmark loop uses FS2 streams for time-based execution control
