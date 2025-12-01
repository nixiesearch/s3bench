# s3bench

A high-performance benchmarking tool for measuring I/O performance on local file systems and S3-compatible storage backends.

## Prerequisites

- Java 11 or higher
- SBT (Scala Build Tool)
- For S3 benchmarking: AWS credentials with read access to your target bucket

## Building

Compile the project:

```bash
sbt compile
```

## Running

s3bench supports two modes: NIO (local file system) and S3 (S3-compatible storage).

### NIO Mode - Local File System Benchmarking

Benchmark local file system performance with direct I/O:

```bash
sbt "run nio --path /path/to/file --bs 4096 --threads 8 --duration 60s"
```

**Parameters:**
- `--path`: Path to the file to benchmark (required)
- `--bs`: Block size in bytes (default: 4096)
- `--threads`: Number of concurrent threads (default: 1)
- `--duration`: Test duration in seconds with 's' suffix (default: 60s)

**Example:**
```bash
# Benchmark a 10GB file with 1MB blocks using 16 threads for 2 minutes
sbt "run nio --path /data/test.bin --bs 1048576 --threads 16 --duration 120s"
```

### S3 Mode - S3-Compatible Storage Benchmarking

Benchmark S3-compatible storage (AWS S3, MinIO, etc.):

```bash
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key
sbt "run s3 --bucket mybucket --prefix path/to/object --endpoint https://s3.amazonaws.com --bs 4096 --threads 8 --duration 60s"
```

**Parameters:**
- `--bucket`: S3 bucket name (required)
- `--prefix`: Object key/prefix (required)
- `--endpoint`: S3 endpoint URL (required)
- `--bs`: Block size in bytes (default: 4096)
- `--threads`: Number of concurrent threads (default: 1)
- `--duration`: Test duration in seconds with 's' suffix (default: 60s)

**Environment Variables:**
- `AWS_ACCESS_KEY_ID`: Your AWS access key
- `AWS_SECRET_ACCESS_KEY`: Your AWS secret key

**Example:**
```bash
# Benchmark MinIO storage
export AWS_ACCESS_KEY_ID=minioadmin
export AWS_SECRET_ACCESS_KEY=minioadmin
sbt "run s3 --bucket test-bucket --prefix data/file.bin --endpoint http://localhost:9000 --bs 65536 --threads 4 --duration 30s"

# Benchmark AWS S3
export AWS_ACCESS_KEY_ID=AKIAIOSFODxxxxxxxxxxxxxx
export AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMIxxxxxxxxxxxxxxxxxxxxx
sbt "run s3 --bucket my-bucket --prefix test-data/large-file.bin --endpoint https://s3.amazonaws.com --bs 1048576 --threads 16 --duration 120s"
```

## Understanding the Output

The tool performs random reads from the target file/object and reports performance metrics including:
- Throughput (MB/s)
- Latency statistics
- IOPS (I/O operations per second)

## Performance Considerations

### Block Size (`--bs`)
- Smaller blocks (4KB-64KB): Better for random read patterns, higher IOPS
- Larger blocks (1MB-4MB): Better for sequential throughput
- Must be aligned with your storage's optimal block size

### Thread Count (`--threads`)
- Start with the number of CPU cores
- Increase for network-bound workloads (S3)
- Decrease if you see diminishing returns or increased latency

### Duration (`--duration`)
- Minimum 30s recommended for stable results
- 60s or longer for production-like measurements
- Longer durations provide more accurate percentile statistics

## Technical Notes

- **NIO mode** uses direct I/O (`O_DIRECT`) to bypass OS page cache for accurate measurements
- **S3 mode** implements AWS Signature Version 4 authentication
- Currently hardcoded to `us-east-1` region for S3 operations
- ByteBuffers are aligned to block size boundaries for optimal performance

