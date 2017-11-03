package io.openmessaging.benchmark.driver.kafka;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;

public class KafkaBenchmarkConsumer implements BenchmarkConsumer {

    private final KafkaConsumer<byte[], byte[]> consumer;

    private final ExecutorService executor;

    public KafkaBenchmarkConsumer(KafkaConsumer<byte[], byte[]> consumer, ConsumerCallback callback) {
        this.consumer = consumer;
        this.executor = Executors.newSingleThreadExecutor();

        this.executor.execute(() -> {
            while (true) {
                ConsumerRecords<byte[], byte[]> records = consumer.poll(100);
                for (ConsumerRecord<byte[], byte[]> record : records) {
                    callback.messageReceived(record.value());

                    Map<TopicPartition, OffsetAndMetadata> offsetMap = new TreeMap<>();
                    offsetMap.put(new TopicPartition(record.topic(), record.partition()),
                            new OffsetAndMetadata(record.offset()));
                    consumer.commitAsync(offsetMap, (offsets, exception) -> {
                        // Offset committed
                    });
                }
            }
        });
    }

    @Override
    public void close() throws Exception {
        consumer.close();
        executor.shutdownNow();
    }

}
