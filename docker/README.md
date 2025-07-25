# OpenMessaging Benchmark Framework Docker

## Precondition

You need to install [Eclipse Temurin 17](https://adoptium.net/)
and set the JAVA_HOME environment variable to its installation directory.

## Building the image

You can use either of the Dockerfiles - `./docker/Dockerfile` or `./docker/Dockerfile.build` based on your needs.

### `Dockerfile`

Uses `eclipse-temurin:17` and takes `BENCHMARK_TARBALL` as an argument.
While using this Dockerfile, you will need to build the project locally **first**.

```
#> mvn build
#> export BENCHMARK_TARBALL=package/target/openmessaging-benchmark-<VERSION>-SNAPSHOT-bin.tar.gz
#> docker build -t openmessaging-benchmark:latest --build-arg BENCHMARK_TARBALL . -f docker/Dockerfile
```

### `Dockerfile.build`

Uses the latest version of `maven` in order to build the project, and then uses `eclipse-temurin:17` as runtime.
This Dockerfile has no dependency (you do not need Maven to be installed locally).

```
#> docker build -t openmessaging-benchmark:latest . -f docker/Dockerfile.build
```

