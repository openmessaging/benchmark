package com.streamlio.messaging.benchmark;

public class Workload {

    public String name;

    /** Number of topics to create in the test */
    public int topics;

    /** Number of partitions each topic will contain */
    public int partitionsPerTopic;

    public int messageSize;

    public int subscriptionsPerTopic;

    public int producersPerTopic;

    public int consumerPerSubscription;

    public int producerRate;
    
    public int testDurationMinutes;
}
