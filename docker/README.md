# OpenMessaging Benchmark Framework Docker

## Precondition

You need to install [Eclipse Temurin 17](https://adoptium.net/) (or 21)
and set the JAVA_HOME environment variable to its installation directory.

## Building the image

You can use one of the Dockerfiles based on your needs:

- `Dockerfile` - requires local build first
- `Dockerfile.build` - builds inside Docker (JDK 17 only)
- `Dockerfile.builder` - **recommended** - multi-JDK support (17 and 21)

### `Dockerfile.builder` (Recommended)

Supports both JDK 17 and JDK 21. Skips formatting checks that can have
JDK version incompatibilities in container environments.

```bash
# Build with JDK 17 (default)
docker build -t omb-benchmark:jdk17 -f docker/Dockerfile.builder .

# Build with JDK 21
docker build -t omb-benchmark:jdk21 --build-arg JDK_VERSION=21 -f docker/Dockerfile.builder .
```

### `Dockerfile`

Uses `eclipse-temurin:17` and takes `BENCHMARK_TARBALL` as an argument.
While using this Dockerfile, you will need to build the project locally **first**.

```bash
mvn install -DskipTests
export BENCHMARK_TARBALL=package/target/openmessaging-benchmark-<VERSION>-SNAPSHOT-bin.tar.gz
docker build -t openmessaging-benchmark:latest --build-arg BENCHMARK_TARBALL . -f docker/Dockerfile
```

### `Dockerfile.build`

Uses Maven to build the project inside Docker, then uses `eclipse-temurin:17` as runtime.
This Dockerfile has no local dependency (you do not need Maven installed locally).

```bash
docker build -t openmessaging-benchmark:latest . -f docker/Dockerfile.build
```

