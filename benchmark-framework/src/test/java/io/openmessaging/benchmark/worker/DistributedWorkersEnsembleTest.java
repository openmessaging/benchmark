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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DistributedWorkersEnsembleTest {

    @ParameterizedTest
    @MethodSource("producerCountExpectations")
    void getNumberOfProducerWorkers(int workerCount, boolean extraConsumers, int expected) {
        List<Worker> workers = mock(List.class);
        when(workers.size()).thenReturn(workerCount);
        assertThat(DistributedWorkersEnsemble.getNumberOfProducerWorkers(workers, extraConsumers))
                .isEqualTo(expected);
    }

    private static Stream<Arguments> producerCountExpectations() {
        return Stream.of(
                Arguments.of(2, true, 1),
                Arguments.of(3, true, 1),
                Arguments.of(4, true, 2),
                Arguments.of(5, true, 2),
                Arguments.of(6, true, 2),
                Arguments.of(7, true, 3),
                Arguments.of(8, true, 3),
                Arguments.of(9, true, 3),
                Arguments.of(2, false, 1),
                Arguments.of(3, false, 1),
                Arguments.of(4, false, 2),
                Arguments.of(5, false, 2),
                Arguments.of(6, false, 3),
                Arguments.of(7, false, 3),
                Arguments.of(8, false, 4),
                Arguments.of(9, false, 4));
    }
}
