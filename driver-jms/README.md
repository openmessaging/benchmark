# General Purpose JMS benchmark

With this driver you can run the benchmark against any messaging system that supports JMS 1.1 or JMS 2.0 API.

For instructions on running the OpenMessaging benchmarks for JMS, see the [official documentation](https://github.com/openmessaging/openmessaging.github.io/blob/source/docs/benchmarks/jms.md).

## Modifying the Openmessaging Benchmark Tar for JMS

Rather than simply dropping a JMS Client Library into `/opt/benchmark/lib` the lib directory tar file is modified.

### DataStax Starlight for JMS / Pulsar Fast JMS

Follow these instructions to compile the openmessaging benchmark for Fast JMS for Apache Pulsar

- Build the openmessaging benchmark package as you would normally

  ```
  mvn clean package
  ```
- Run the repacking script

  ```
  bash driver-jms/package-pulsar.sh
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

- Build the openmessaging benchmark package

  ```
  mvn clean package
  ```
- Run the repacking script passing in the location of the fat jar. EG. `~/kafka-jms-client/target/kafka-jms-client-fat-6.2.1.jar`

  ```
  bash driver-jms/package-kafka.sh /path/to/the/kafka-jms-client.jar
  ```

You can now deploy to AWS from `driver-kafka/deploy`.

## JMS Driver Benchmarks

For Pulsar JMS (and likely Kafka) you will likely want to allocate additional consumer clients.

- Edit your `terraform.tfvars` file to adjust `num_instances["client"]`.
- Run `bin/benchmark` with the `--extra` option to allocate more workers as consumers.

[1]: https://docs.confluent.io/platform/current/clients/kafka-jms-client/installation.html#appendix-1

