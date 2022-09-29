## Build Pravega

To use a pre-release version of Pravega, you must build it manually using the following steps:

```
git clone https://github.com/pravega/pravega
cd pravega
git checkout master
./gradlew install distTar
```

This will build the file `pravega/build/distributions/pravega-0.9.0.tgz.`
Then comment `pravegaSrc` and `pravegaSrcRemote: yes` and uncomment `pravegaSrc` `pravegaSrcRemote: no` in `driver-pravega/deploy/deploy.yaml`

```
# Change below to use a published release of Pravega or a local build.
# pravegaSrc: "https://github.com/pravega/pravega/releases/download/v{{ pravegaVersion }}/pravega-{{ pravegaVersion }}.tgz"
# pravegaSrcRemote: yes
# Here is the file path for local Pravega build
pravegaSrc: "../../../pravega/build/distributions/pravega-{{ pravegaVersion }}.tgz"
pravegaSrcRemote: no
```

If needed, change the variable `pravegaVersion` in [vars.yaml](../deploy/vars.yaml) to match the version built.

If needed, change [pom.xml](../pom.xml) to match the version built.

## Build Benchmark

Add flag to skip license check`-Dlicense.skip=true` if license check failed.

```
mvn clean install
```

