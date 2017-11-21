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
package io.openmessaging.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TestResult {
    public String workload;
    public String driver;

    public List<Double> publishRate = new ArrayList<>();
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

    public double aggregatedPublishLatencyAvg;
    public double aggregatedPublishLatency50pct;
    public double aggregatedPublishLatency75pct;
    public double aggregatedPublishLatency95pct;
    public double aggregatedPublishLatency99pct;
    public double aggregatedPublishLatency999pct;
    public double aggregatedPublishLatency9999pct;
    public double aggregatedPublishLatencyMax;

    public Map<Double, Double> aggregatedPublishLatencyQuantiles = new TreeMap<>();

    public List<Double> e2eLatencyAvg = new ArrayList<>();
    public List<Double> e2eLatency50pct = new ArrayList<>();
    public List<Double> e2eLatency75pct = new ArrayList<>();
    public List<Double> e2eLatency95pct = new ArrayList<>();
    public List<Double> e2eLatency99pct = new ArrayList<>();
    public List<Double> e2eLatency999pct = new ArrayList<>();
    public List<Double> e2eLatency9999pct = new ArrayList<>();
    public List<Double> e2eLatencyMax = new ArrayList<>();

    public Map<Double, Double> aggregatedE2eLatencyQuantiles = new TreeMap<>();

    public double aggregatedE2eLatencyAvg;
    public double aggregatedE2eLatency50pct;
    public double aggregatedE2eLatency75pct;
    public double aggregatedE2eLatency95pct;
    public double aggregatedE2eLatency99pct;
    public double aggregatedE2eLatency999pct;
    public double aggregatedE2eLatency9999pct;
    public double aggregatedE2eLatencyMax;
}
