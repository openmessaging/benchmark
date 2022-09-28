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
package io.openmessaging.benchmark.worker;


import io.openmessaging.benchmark.worker.commands.ConsumerAssignment;
import io.openmessaging.benchmark.worker.commands.CountersStats;
import io.openmessaging.benchmark.worker.commands.CumulativeLatencies;
import io.openmessaging.benchmark.worker.commands.PeriodStats;
import io.openmessaging.benchmark.worker.commands.ProducerWorkAssignment;
import io.openmessaging.benchmark.worker.commands.TopicsInfo;
import java.io.File;
import java.io.IOException;
import java.util.List;

public interface Worker extends AutoCloseable {

    void initializeDriver(File configurationFile) throws IOException;

    List<String> createTopics(TopicsInfo topicsInfo) throws IOException;

    void createProducers(List<String> topics) throws IOException;

    void createConsumers(ConsumerAssignment consumerAssignment) throws IOException;

    void probeProducers() throws IOException;

    void startLoad(ProducerWorkAssignment producerWorkAssignment) throws IOException;

    void adjustPublishRate(double publishRate) throws IOException;

    void pauseConsumers() throws IOException;

    void resumeConsumers() throws IOException;

    CountersStats getCountersStats() throws IOException;

    PeriodStats getPeriodStats() throws IOException;

    CumulativeLatencies getCumulativeLatencies() throws IOException;

    void resetStats() throws IOException;

    void stopAll();

    String id();
}
