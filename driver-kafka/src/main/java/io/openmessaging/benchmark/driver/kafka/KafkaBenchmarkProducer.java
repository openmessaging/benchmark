package io.openmessaging.benchmark.driver.kafka;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import io.openmessaging.benchmark.driver.BenchmarkProducer;

public class KafkaBenchmarkProducer implements BenchmarkProducer {

    private final KafkaProducer<byte[], byte[]> producer;
    private final String topic;

    public KafkaBenchmarkProducer(KafkaProducer<byte[], byte[]> producer, String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    @Override
    public CompletableFuture<Void> sendAsync(byte[] message) {
        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(topic, message);

        CompletableFuture<Void> future = new CompletableFuture<>();

        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                future.completeExceptionally(exception);
            } else {
                future.complete(null);
            }
        });

        return future;
    }

    @Override
    public void close() throws Exception {
        producer.close();
    }

}
