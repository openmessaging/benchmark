install:
  mvn install -DskipTests  -Dlicense.skip=true

benchmark DRIVERS="driver-gateway/gateway-latency.yaml" WORKLOADS="workloads/1-topic-1-partition-100b.yaml": 
  docker exec -it openmessaging-benchmark-kafka-client $PWD/bin/benchmark --drivers {{DRIVERS}} {{WORKLOADS}}