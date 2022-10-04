package io.openmessaging.benchmark.driver.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QueueTypeTest {

    @Test
    public void classic() {
        assertThat(RabbitMqConfig.QueueType.CLASSIC.queueOptions()).isEmpty();
    }

    @Test
    public void quorum() {
        assertThat(RabbitMqConfig.QueueType.QUORUM.queueOptions())
                .satisfies(
                        o -> {
                            assertThat(o).containsEntry("x-queue-type", "quorum");
                            assertThat(o).hasSize(1);
                        });
    }
}
