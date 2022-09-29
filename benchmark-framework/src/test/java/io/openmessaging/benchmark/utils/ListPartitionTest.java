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

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ListPartitionTest {

    @Test
    void partitionList() {
        List<Integer> list = asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<List<Integer>> lists = ListPartition.partitionList(list, 3);
        assertThat(lists)
                .satisfies(
                        s -> {
                            assertThat(s).hasSize(3);
                            assertThat(s.get(0)).isEqualTo(asList(1, 4, 7, 10));
                            assertThat(s.get(1)).isEqualTo(asList(2, 5, 8));
                            assertThat(s.get(2)).isEqualTo(asList(3, 6, 9));
                        });
    }
}
