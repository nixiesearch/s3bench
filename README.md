# S3 benchmarking tool

A simple tool to ease the [AWS S3](https://aws.amazon.com/pm/serv-s3/?nis=8) getObject benchmarking.

## Usage

Download the pre-build JAR file from [the releases page](https://github.com/nixiesearch/s3bench/releases) and run it with Java 11+.

```shell
$ java -jar s3bench.jar --help

Usage: s3bench  <options>
Options:

  -b, --bucket  <arg>     S3 bucket (required)
  -e, --endpoint  <arg>   S3 endpoint URL (optional)
  -p, --prefix  <arg>     S3 prefix (required)
      --region  <arg>     AWS region (optional, default us-east-1)
  -r, --requests  <arg>   how many requests to perform (optional, default 100)
  -t, --threads  <arg>    How many parallel clients to spawn (optional, default
                          1)
  -h, --help              Show help message

For all other tricks, consult the docs on https://github.com/nixiesearch/s3bench
```

## License

Apache 2.0