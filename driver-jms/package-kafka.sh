# the one argument is the path to the confluent jms client fat jar on your local system

cd package/target
gunzip openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar.gz 
mkdir -p openmessaging-benchmark-0.0.1-SNAPSHOT/lib 
cp -p ${1} openmessaging-benchmark-0.0.1-SNAPSHOT/lib/.
tar --append --file=openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar openmessaging-benchmark-0.0.1-SNAPSHOT/lib/kafka-jms-client-fat-6.2.1.jar
gzip openmessaging-benchmark-0.0.1-SNAPSHOT-bin.tar

