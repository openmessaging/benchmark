install:
  mvn install -DskipTests  -Dlicense.skip=true


docker-start:
    docker-compose up -d

docker-recreate-gateway: install
    docker-compose up -d --force-recreate conduktor-gateway

## Some specific encryption benchmarks (sadly they don't matrix)
# just benchmark driver-gateway/gateway-latency-gatewaykms-fle.yaml workloads/1-topic-3-partition-json-3producers-with-key.yaml
# just benchmark driver-gateway/gateway-latency-recordkey-fle.yaml workloads/1-topic-3-partition-json-3producers-with-key.yaml
# just benchmark driver-gateway/gateway-latency-gatewaykms-fe.yaml workloads/1-topic-1-partition-1kb-with-key.yaml
# just benchmark driver-gateway/gateway-latency-recordkey-fe.yaml workloads/1-topic-1-partition-1kb-with-key.yaml

benchmark DRIVERS="driver-gateway/gateway-latency.yaml" WORKLOADS="workloads/1-topic-1-partition-100b.yaml": 
  docker exec -it openmessaging-benchmark-kafka-client $PWD/bin/benchmark --drivers {{DRIVERS}} {{WORKLOADS}}

consume-kafka TOPIC:
  docker compose exec -it kafka-client kafka-console-consumer --bootstrap-server kafka-1:9092 --topic {{TOPIC}} --from-beginning --property print.key=true --property key.separator="|"

consume-gateway TOPIC:
  docker compose exec -it kafka-client kafka-console-consumer --bootstrap-server conduktor-gateway:6969 --topic {{TOPIC}} --from-beginning --property print.key=true --property key.separator="|"

list-topics:
  docker compose exec -it kafka-client kafka-topics --bootstrap-server conduktor-gateway:6969 --list 
