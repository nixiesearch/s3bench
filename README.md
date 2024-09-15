# S3 benchmarking tool

A simple tool to ease the S3 getObject benchmarking.

## Usage

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