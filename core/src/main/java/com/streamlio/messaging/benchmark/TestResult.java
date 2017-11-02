package com.streamlio.messaging.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TestResult {
    public String workload;
    public String driver;

    public List<Double> publishRate = new ArrayList<>();
    public List<Double> consumeRate = new ArrayList<>();

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
}
