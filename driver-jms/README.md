# General Purpose JMS benchmark

With this driver you can run the benchmark against any messaging system that supports JMS 1.1 or JMS 2.0 API.

For instructions on running the OpenMessaging benchmarks for JMS, see the [official documentation](https://github.com/openmessaging/openmessaging.github.io/blob/source/docs/benchmarks/jms.md).

## Modifying the Openmessaging Benchmark Tar for JMS

Rather than simply dropping a JMS Client Library into `/opt/benchmark/lib` the lib directory tar file is modified.

### DataStax Starlight for JMS / Pulsar JMS

Follow these instructions to compile the openmessaging benchmark for Fast JMS for Apache Pulsar

- Build the package
  ```
  mvn clean install
  ```
- Go to the `package/target` directory
  ```
  cd package/target
  ```
- Unpack the package
  ```
  tar zxvf openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz
  cd openmessaging-benchmark-0.0.1-SNAPSHOT
  ```
- Remove Pulsar and Jackson jar files:
  ```
  rm lib/org.apache.pulsar-*
  rm lib/*jackson*
  ```
- Add the Pulsar JMS file:
  ```
  curl https://repo1.maven.org/maven2/com/datastax/oss/pulsar-jms-all/1.2.2/pulsar-jms-all-1.2.2.jar -o lib/pulsar-jms-all-1.2.2.jar
  ```
- Create a new .tar.gz package
  ```
  cd ..
  rm openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz
  tar zcvf openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz openmessaging-benchmark-0.0.1-SNAPSHOT
  ```

You can now deploy to AWS from `driver-pulsar/deploy`.

### Confluent JMS Client / Kafka JMS

Follow the [Confluent instructions][1] to create a fat jar.

- Create a directory
  ```
  cd ~
  mkdir kafka-jms-client
  cd kafka-jms-client
  ```
- Create the pom.xml
- Change `<url>http://packages.confluent.io/maven/</url>` to `<url>https://packages.confluent.io/maven/</url>`
- Build the fat jar
  ```
  mvn clean package
  ```

Follow these instructions to compile the openmessaging benchmark for Confluent JMS Client

- Build the package
  ```
  mvn clean install
  ```
- Go to the `package/target` directory
  ```
  cd package/target
  ```
- Append the fat jar into the openmessaging benchmark tar file
  ```
  gunzip openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz 
  mkdir -p openmessaging-benchmark-0.0.1-SNAPSHOT/lib 
  cp -p ~/kafka-jms-client/target/kafka-jms-client-fat-6.2.1.jar openmessaging-benchmark-0.0.1-SNAPSHOT/lib/.
  tar --append --file=openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar openmessaging-benchmark-0.0.1-SNAPSHOT/lib/kafka-jms-client-fat-6.2.1.jar		
  gzip openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar
  ```



[1]: https://docs.confluent.io/platform/current/clients/kafka-jms-client/installation.html#appendix-1