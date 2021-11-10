cd package/target
tar zxvf openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz
cd openmessaging-benchmark-0.0.1-SNAPSHOT
rm lib/org.apache.pulsar-*
rm lib/*jackson*
curl https://repo1.maven.org/maven2/com/datastax/oss/pulsar-jms-all/1.2.2/pulsar-jms-all-1.2.2.jar -o lib/pulsar-jms-all-1.2.2.jar
cd ..
rm openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz
tar zcvf openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz openmessaging-benchmark-0.0.1-SNAPSHOT

