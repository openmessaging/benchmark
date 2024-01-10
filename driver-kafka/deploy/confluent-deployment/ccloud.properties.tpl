bootstrap.servers=CCLOUD_BOOTSTRAP_SERVER:9092
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="CCLOUD API KEY" password="CCLOUD API SECRET";
security.protocol=SASL_SSL
ssl.endpoint.identification.algorithm=https
request.timeout.ms=300000
max.in.flight.requests.per.connection=5

