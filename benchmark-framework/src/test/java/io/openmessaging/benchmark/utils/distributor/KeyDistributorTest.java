/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.openmessaging.benchmark.utils.distributor;

import static io.openmessaging.benchmark.utils.distributor.KeyDistributorType.KEY_ROUND_ROBIN;
import static io.openmessaging.benchmark.utils.distributor.KeyDistributorType.RANDOM_NANO;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class KeyDistributorTest {

    private KeyDistributor roundRobin;
    private KeyDistributor randomNano;

    @Before
    public void init() {
        roundRobin = KeyDistributor.build(KEY_ROUND_ROBIN);
        randomNano = KeyDistributor.build(RANDOM_NANO);
    }

    @Test
    public void testRoundRobin() {
        // Given-When
        Map<String, Long> collect = Stream.generate(() -> roundRobin.next())
                .limit(10_000)
                .collect(groupingBy(Function.identity(), counting()));

        // Then
        assertEquals(10_000, collect.size());
    }

    /**
     * This test results hits count ~6_000 from 10_000.
     * It is left ignored for testing purpose for optimization of algorithm.
     */
    @Test
    @Ignore
    public void testRandomNano() {
        // Given-When
        Map<String, Long> collect = Stream.generate(() -> randomNano.next())
                .limit(10_000)
                .collect(groupingBy(Function.identity(), counting()));

        // Then
        assertEquals(10_000, collect.size());
    }

}