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
package io.openmessaging.benchmark.utils.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageSizeDistributionTest {

    @Test
    void parsesBasicRanges() {
        Map<String, Integer> config = new LinkedHashMap<>();
        config.put("0-256", 100);
        config.put("256-1024", 200);
        config.put("1024-4096", 300);

        MessageSizeDistribution dist = new MessageSizeDistribution(config);

        assertThat(dist.getBucketCount()).isEqualTo(3);
        assertThat(dist.getTotalWeight()).isEqualTo(600);
    }

    @Test
    void parsesSizeSuffixes() {
        Map<String, Integer> config = new LinkedHashMap<>();
        config.put("0-1KB", 100);
        config.put("1KB-1MB", 200);

        MessageSizeDistribution dist = new MessageSizeDistribution(config);
        List<Integer> sizes = dist.getBucketSizes();

        // Midpoint of 0-1024 = 512
        assertThat(sizes.get(0)).isEqualTo(512);
        // Midpoint of 1024-1048576 = 524800
        assertThat(sizes.get(1)).isEqualTo(524800);
    }

    @Test
    void calculatesBucketSizesAsMidpoint() {
        Map<String, Integer> config = new LinkedHashMap<>();
        config.put("0-100", 1);
        config.put("100-200", 1);
        config.put("200-300", 1);

        MessageSizeDistribution dist = new MessageSizeDistribution(config);
        List<Integer> sizes = dist.getBucketSizes();

        assertThat(sizes).containsExactly(50, 150, 250);
    }

    @Test
    void calculatesWeightsArray() {
        Map<String, Integer> config = new LinkedHashMap<>();
        config.put("0-256", 234);
        config.put("256-1024", 456);
        config.put("1024-4096", 678);

        MessageSizeDistribution dist = new MessageSizeDistribution(config);
        int[] weights = dist.getWeights();

        assertThat(weights).containsExactly(234, 456, 678);
    }

    @Test
    void calculatesCumulativeWeights() {
        Map<String, Integer> config = new LinkedHashMap<>();
        config.put("0-256", 100);
        config.put("256-1024", 200);
        config.put("1024-4096", 300);

        MessageSizeDistribution dist = new MessageSizeDistribution(config);
        int[] cumulative = dist.getCumulativeWeights();

        assertThat(cumulative).containsExactly(100, 300, 600);
    }

    @Test
    void calculatesAverageSize() {
        Map<String, Integer> config = new LinkedHashMap<>();
        config.put("0-100", 1); // midpoint 50, weight 1
        config.put("100-200", 1); // midpoint 150, weight 1

        MessageSizeDistribution dist = new MessageSizeDistribution(config);

        // (50 * 1 + 150 * 1) / 2 = 100
        assertThat(dist.getAvgSize()).isEqualTo(100);
    }

    @Test
    void calculatesMaxSize() {
        Map<String, Integer> config = new LinkedHashMap<>();
        config.put("0-256", 100);
        config.put("256-1024", 200);
        config.put("1024-5000", 300);

        MessageSizeDistribution dist = new MessageSizeDistribution(config);

        assertThat(dist.getMaxSize()).isEqualTo(5000);
    }

    @Test
    void handlesProductionDistribution() {
        // Real production distribution from the plan
        Map<String, Integer> config = new LinkedHashMap<>();
        config.put("0-256", 234);
        config.put("256-1024", 456);
        config.put("1024-4096", 678);
        config.put("4096-16384", 312);
        config.put("16384-65536", 98);
        config.put("65536-262144", 45);
        config.put("262144-1048576", 18);
        config.put("1048576-5242880", 6);

        MessageSizeDistribution dist = new MessageSizeDistribution(config);

        assertThat(dist.getBucketCount()).isEqualTo(8);
        assertThat(dist.getTotalWeight()).isEqualTo(1847);
        assertThat(dist.getMaxSize()).isEqualTo(5242880); // 5MB
        assertThat(dist.getBucketSizes()).hasSize(8);
        assertThat(dist.getWeights()).hasSize(8);
    }

    @Test
    void throwsOnEmptyConfig() {
        assertThatThrownBy(() -> new MessageSizeDistribution(new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnNullConfig() {
        assertThatThrownBy(() -> new MessageSizeDistribution(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnInvalidRangeFormat() {
        Map<String, Integer> config = new HashMap<>();
        config.put("invalid", 100);

        assertThatThrownBy(() -> new MessageSizeDistribution(config))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnNegativeWeight() {
        Map<String, Integer> config = new HashMap<>();
        config.put("0-100", -1);

        assertThatThrownBy(() -> new MessageSizeDistribution(config))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnZeroTotalWeight() {
        Map<String, Integer> config = new HashMap<>();
        config.put("0-100", 0);
        config.put("100-200", 0);

        assertThatThrownBy(() -> new MessageSizeDistribution(config))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnInvalidMinMax() {
        Map<String, Integer> config = new HashMap<>();
        config.put("200-100", 1); // max < min

        assertThatThrownBy(() -> new MessageSizeDistribution(config))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weightedSelectionProducesCorrectDistribution() {
        Map<String, Integer> config = new LinkedHashMap<>();
        config.put("0-100", 100); // 50% weight
        config.put("100-200", 100); // 50% weight

        MessageSizeDistribution dist = new MessageSizeDistribution(config);
        int[] cumulative = dist.getCumulativeWeights();
        int totalWeight = dist.getTotalWeight();

        // Simulate weighted selection like LocalWorker does
        int[] counts = new int[2];
        int iterations = 100000;

        for (int i = 0; i < iterations; i++) {
            int target = i % totalWeight; // Deterministic for testing
            int idx = Arrays.binarySearch(cumulative, target + 1);
            if (idx < 0) {
                idx = -idx - 1;
            }
            idx = Math.min(idx, 1);
            counts[idx]++;
        }

        // Each bucket should get approximately 50% of selections
        double ratio0 = (double) counts[0] / iterations;
        double ratio1 = (double) counts[1] / iterations;

        assertThat(ratio0).isBetween(0.49, 0.51);
        assertThat(ratio1).isBetween(0.49, 0.51);
    }
}

