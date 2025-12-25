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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses and represents a message size distribution from workload config.
 * Creates one payload size per bucket and provides weights for runtime selection.
 *
 * <p>Example configuration:
 * <pre>
 * messageSizeDistribution:
 *   "0-256": 234
 *   "256-1024": 456
 *   "1024-4096": 678
 * </pre>
 */
public class MessageSizeDistribution {

    private final List<Bucket> buckets;
    private final int totalWeight;

    /**
     * Represents a single size bucket with min/max range and weight.
     */
    public static class Bucket {
        public final int minSize;
        public final int maxSize;
        public final int weight;

        Bucket(int minSize, int maxSize, int weight) {
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.weight = weight;
        }

        /**
         * Returns midpoint of range as the representative size for this bucket.
         *
         * @return the representative size (midpoint of min and max)
         */
        public int getRepresentativeSize() {
            return (minSize + maxSize) / 2;
        }
    }

    public MessageSizeDistribution(Map<String, Integer> config) {
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("Distribution config cannot be null or empty");
        }

        List<Bucket> parsed = new ArrayList<>();
        int total = 0;

        for (Map.Entry<String, Integer> e : config.entrySet()) {
            int[] range = parseRange(e.getKey());
            int weight = e.getValue();
            if (weight < 0) {
                throw new IllegalArgumentException("Weight cannot be negative: " + weight);
            }
            parsed.add(new Bucket(range[0], range[1], weight));
            total += weight;
        }

        this.buckets = parsed;
        this.totalWeight = total;

        if (totalWeight <= 0) {
            throw new IllegalArgumentException("Distribution weights must sum to a positive value");
        }
    }

    private int[] parseRange(String range) {
        if (range == null || range.isEmpty()) {
            throw new IllegalArgumentException("Range cannot be null or empty");
        }

        // Find the last hyphen that separates min and max (to handle negative numbers if any)
        int lastHyphen = range.lastIndexOf('-');
        if (lastHyphen <= 0) {
            throw new IllegalArgumentException("Invalid range format (expected 'min-max'): " + range);
        }

        String minPart = range.substring(0, lastHyphen);
        String maxPart = range.substring(lastHyphen + 1);

        int minSize = parseSize(minPart);
        int maxSize = parseSize(maxPart);

        if (minSize < 0) {
            throw new IllegalArgumentException("Min size cannot be negative: " + minSize);
        }
        if (maxSize < minSize) {
            throw new IllegalArgumentException(
                    "Max size must be >= min size: " + minSize + " > " + maxSize);
        }

        return new int[] {minSize, maxSize};
    }

    private int parseSize(String s) {
        s = s.trim().toUpperCase();
        int mult = 1;

        if (s.endsWith("KB")) {
            mult = 1024;
            s = s.substring(0, s.length() - 2);
        } else if (s.endsWith("MB")) {
            mult = 1024 * 1024;
            s = s.substring(0, s.length() - 2);
        } else if (s.endsWith("B")) {
            s = s.substring(0, s.length() - 1);
        }

        try {
            return Integer.parseInt(s.trim()) * mult;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid size format: " + s, e);
        }
    }

    /**
     * Returns list of representative sizes, one per bucket (for payload generation).
     *
     * @return list of representative sizes
     */
    public List<Integer> getBucketSizes() {
        List<Integer> sizes = new ArrayList<>();
        for (Bucket b : buckets) {
            sizes.add(b.getRepresentativeSize());
        }
        return sizes;
    }

    /**
     * Returns weights array matching bucket order (for runtime selection).
     *
     * @return array of weights for each bucket
     */
    public int[] getWeights() {
        int[] weights = new int[buckets.size()];
        for (int i = 0; i < buckets.size(); i++) {
            weights[i] = buckets.get(i).weight;
        }
        return weights;
    }

    /**
     * Returns cumulative weights array for O(log n) binary search selection.
     *
     * @return array of cumulative weights
     */
    public int[] getCumulativeWeights() {
        int[] cumulative = new int[buckets.size()];
        int sum = 0;
        for (int i = 0; i < buckets.size(); i++) {
            sum += buckets.get(i).weight;
            cumulative[i] = sum;
        }
        return cumulative;
    }

    public int getTotalWeight() {
        return totalWeight;
    }

    public int getMaxSize() {
        return buckets.stream().mapToInt(b -> b.maxSize).max().orElse(0);
    }

    /**
     * Returns weighted average size across all buckets.
     *
     * @return the weighted average size
     */
    public int getAvgSize() {
        long sum = 0;
        for (Bucket b : buckets) {
            sum += (long) b.getRepresentativeSize() * b.weight;
        }
        return (int) (sum / totalWeight);
    }

    public int getBucketCount() {
        return buckets.size();
    }

    public List<Bucket> getBuckets() {
        return buckets;
    }
}

