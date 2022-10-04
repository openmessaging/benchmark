package io.openmessaging.benchmark.driver.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RabbitMqConfigTest {

    @Test
    public void deserialize() throws JsonProcessingException {
        String config =
                "{\"amqpUris\":[\"amqp://local\"],\"messagePersistence\":true,\"queueType\":\"QUORUM\"}";
        RabbitMqConfig value = new ObjectMapper().readValue(config, RabbitMqConfig.class);
        assertThat(value)
                .satisfies(
                        v -> {
                            assertThat(v.amqpUris).containsOnly("amqp://local");
                            assertThat(v.messagePersistence).isTrue();
                            assertThat(v.queueType).isEqualTo(RabbitMqConfig.QueueType.QUORUM);
                        });
    }

    @Test
    public void deserializeWithDefaults() throws JsonProcessingException {
        String config = "{\"amqpUris\":[\"amqp://local\"]}";
        RabbitMqConfig value = new ObjectMapper().readValue(config, RabbitMqConfig.class);
        assertThat(value)
                .satisfies(
                        v -> {
                            assertThat(v.amqpUris).containsOnly("amqp://local");
                            assertThat(v.messagePersistence).isFalse();
                            assertThat(v.queueType).isEqualTo(RabbitMqConfig.QueueType.CLASSIC);
                        });
    }
}
