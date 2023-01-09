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
package io.openmessaging.benchmark.utils;

import static java.util.Collections.singletonList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Batcher<T> {

    private final int maxBatchSize;

    public Batcher(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public List<List<T>> batch(List<T> list) {
        if (maxBatchSize < 1) {
            return singletonList(list);
        }
        AtomicInteger counter = new AtomicInteger();
        return new ArrayList<>(
                list.stream()
                        .collect(Collectors.groupingBy(gr -> counter.getAndIncrement() / maxBatchSize))
                        .values());
    }
}
