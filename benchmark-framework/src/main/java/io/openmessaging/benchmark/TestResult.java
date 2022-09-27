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
package io.openmessaging.benchmark;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TestResult {
    public String workload;
    public String driver;
    public long messageSize;
    public int topics;
    public int partitions;
    public int producersPerTopic;
    public int consumersPerTopic;

    public List<Double> publishRate = new ArrayList<>();
    public List<Double> publishErrorRate = new ArrayList<>();
    public List<Double> consumeRate = new ArrayList<>();
    public List<Long> backlog = new ArrayList<>();

    public List<Double> publishLatencyAvg = new ArrayList<>();
    public List<Double> publishLatency50pct = new ArrayList<>();
    public List<Double> publishLatency75pct = new ArrayList<>();
    public List<Double> publishLatency95pct = new ArrayList<>();
    public List<Double> publishLatency99pct = new ArrayList<>();
    public List<Double> publishLatency999pct = new ArrayList<>();
    public List<Double> publishLatency9999pct = new ArrayList<>();
    public List<Double> publishLatencyMax = new ArrayList<>();

    public List<Double> publishDelayLatencyAvg = new ArrayList<>();
    public List<Long> publishDelayLatency50pct = new ArrayList<>();
    public List<Long> publishDelayLatency75pct = new ArrayList<>();
    public List<Long> publishDelayLatency95pct = new ArrayList<>();
    public List<Long> publishDelayLatency99pct = new ArrayList<>();
    public List<Long> publishDelayLatency999pct = new ArrayList<>();
    public List<Long> publishDelayLatency9999pct = new ArrayList<>();
    public List<Long> publishDelayLatencyMax = new ArrayList<>();

    public double aggregatedPublishLatencyAvg;
    public double aggregatedPublishLatency50pct;
    public double aggregatedPublishLatency75pct;
    public double aggregatedPublishLatency95pct;
    public double aggregatedPublishLatency99pct;
    public double aggregatedPublishLatency999pct;
    public double aggregatedPublishLatency9999pct;
    public double aggregatedPublishLatencyMax;

    public double aggregatedPublishDelayLatencyAvg;
    public long aggregatedPublishDelayLatency50pct;
    public long aggregatedPublishDelayLatency75pct;
    public long aggregatedPublishDelayLatency95pct;
    public long aggregatedPublishDelayLatency99pct;
    public long aggregatedPublishDelayLatency999pct;
    public long aggregatedPublishDelayLatency9999pct;
    public long aggregatedPublishDelayLatencyMax;

    public Map<Double, Double> aggregatedPublishLatencyQuantiles = new TreeMap<>();

    public Map<Double, Long> aggregatedPublishDelayLatencyQuantiles = new TreeMap<>();

    // End to end latencies (from producer to consumer)
    // Latencies are expressed in milliseconds (without decimals)

    public List<Double> endToEndLatencyAvg = new ArrayList<>();
    public List<Double> endToEndLatency50pct = new ArrayList<>();
    public List<Double> endToEndLatency75pct = new ArrayList<>();
    public List<Double> endToEndLatency95pct = new ArrayList<>();
    public List<Double> endToEndLatency99pct = new ArrayList<>();
    public List<Double> endToEndLatency999pct = new ArrayList<>();
    public List<Double> endToEndLatency9999pct = new ArrayList<>();
    public List<Double> endToEndLatencyMax = new ArrayList<>();

    public Map<Double, Double> aggregatedEndToEndLatencyQuantiles = new TreeMap<>();

    public double aggregatedEndToEndLatencyAvg;
    public double aggregatedEndToEndLatency50pct;
    public double aggregatedEndToEndLatency75pct;
    public double aggregatedEndToEndLatency95pct;
    public double aggregatedEndToEndLatency99pct;
    public double aggregatedEndToEndLatency999pct;
    public double aggregatedEndToEndLatency9999pct;
    public double aggregatedEndToEndLatencyMax;

    public int getTopics() {
        return topics;
    }

    public int getPartitions() {
        return partitions;
    }

    public long getMessageSize() {
        return messageSize;
    }
}
