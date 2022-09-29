/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openmessaging.benchmark.driver.kafka;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openmessaging.benchmark.driver.BenchmarkDriver.TopicInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaTopicCreatorTest {
    private final Map<String, String> topicConfigs = new HashMap<>();
    private final String topic = "topic";
    private final int partitions = 1;
    private final short replicationFactor = 1;
    private final TopicInfo topicInfo = new TopicInfo(topic, partitions);
    @Mock private AdminClient admin;
    @Mock private CreateTopicsResult createTopicsResult;
    @Captor private ArgumentCaptor<List<NewTopic>> captor;
    private KafkaTopicCreator topicCreator;

    @BeforeEach
    void beforeEach() {
        int maxBatchSize = 1;
        topicCreator = new KafkaTopicCreator(admin, topicConfigs, replicationFactor, maxBatchSize);

        when(admin.createTopics(any())).thenAnswer(__ -> createTopicsResult);
    }

    @Test
    void created() {
        KafkaFuture<Void> future = KafkaFuture.completedFuture(null);

        when(createTopicsResult.values()).thenReturn(singletonMap(topic, future));

        topicCreator.create(singletonList(topicInfo)).join();

        verify(admin).createTopics(captor.capture());

        List<List<NewTopic>> allValues = captor.getAllValues();
        assertThat(allValues).hasSize(1);
        assertNewTopics(allValues.get(0));
    }

    @Test
    void topicExists() {
        KafkaFutureImpl<Void> future = new KafkaFutureImpl<>();
        future.completeExceptionally(new TopicExistsException(null));

        when(createTopicsResult.values()).thenReturn(singletonMap(topic, future));

        topicCreator.create(singletonList(topicInfo)).join();

        verify(admin).createTopics(captor.capture());

        List<List<NewTopic>> allValues = captor.getAllValues();
        assertThat(allValues).hasSize(1);
        assertNewTopics(allValues.get(0));
    }

    @Test
    void timeout() {
        KafkaFutureImpl<Void> future1 = new KafkaFutureImpl<>();
        future1.completeExceptionally(new TimeoutException());
        KafkaFuture<Void> future2 = KafkaFuture.completedFuture(null);

        when(createTopicsResult.values())
                .thenReturn(singletonMap(topic, future1))
                .thenReturn(singletonMap(topic, future2));

        topicCreator.create(singletonList(topicInfo)).join();

        verify(admin, times(2)).createTopics(captor.capture());

        List<List<NewTopic>> allValues = captor.getAllValues();
        assertThat(allValues).hasSize(2);
        assertNewTopics(allValues.get(0));
        assertNewTopics(allValues.get(1));
    }

    private void assertNewTopics(List<NewTopic> newTopics) {
        assertThat(newTopics).hasSize(1);
        NewTopic newTopic = newTopics.get(0);
        assertThat(newTopic.name()).isEqualTo(topic);
        assertThat(newTopic.numPartitions()).isEqualTo(partitions);
        assertThat(newTopic.replicationFactor()).isEqualTo(replicationFactor);
        assertThat(newTopic.configs()).isSameAs(topicConfigs);
    }
}
