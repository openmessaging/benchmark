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
package io.openmessaging.benchmark.driver.natsJetStream;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.time.Duration;

import io.nats.client.*;
import io.nats.client.api.*;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;

import java.io.File;
import java.io.IOException;

import java.util.concurrent.CompletableFuture;

import org.apache.bookkeeper.stats.StatsLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsBenchmarkDriver implements BenchmarkDriver {
    private NatsConfig config;
    @Override public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        config = mapper.readValue(configurationFile, NatsConfig.class);
        log.info("read config file," + config.toString());
    }

    @Override public String getTopicNamePrefix() {
        return "Nats-benchmark";
    }

    @Override public CompletableFuture<Void> createTopic(String sub, int partitions) {
        log.info("nats create a topic" + sub);
        log.info("ignore partitions");

        Connection con;
        JetStreamManagement jsm;

        try {
            con = Nats.connect(normalizedOptions());
            jsm = con.jetStreamManagement();

            StreamConfiguration streamConfig = StreamConfiguration.builder()
                    .name("Str-" + sub)
                    .subjects(sub)
                    .storageType(StorageType.File)
                    .replicas(config.jsReplicas)
                    .build();
            jsm.addStream(streamConfig);

            con.flush(Duration.ZERO);

            log.info("JetStream [" + "Str-" + sub + "] created");
        } catch (Exception e) {
            log.error("createTopic exception " + e);
            return null;
        }

        try {
            ConsumerConfiguration.Builder b = ConsumerConfiguration.builder();

            b.durable("Con-" + sub);
            b.ackPolicy(AckPolicy.Explicit);
            b.ackWait(Duration.ofSeconds(30));

            if (config.jsConsumerMode.equalsIgnoreCase("push")) {
                // Push JS Consumers present as a stream at a fixed subject
                b.deliverSubject("push." + sub).deliverGroup("Grp-" + sub);
            }

            ConsumerConfiguration cc = b.build();

            jsm.addOrUpdateConsumer("Str-" + sub, cc);

            log.info("JS Consumer [" + "Con-" + sub + "] created as [" + config.jsConsumerMode + "] mode");
        } catch (Exception e) {
            log.error("create JS consumer error" + e);
            return null;
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        future.complete(null);
        return future;
    }

    @Override public CompletableFuture<BenchmarkProducer> createProducer(String sub) {
        Connection con;
        JetStream js;

        log.info("createProducer");
        log.info("topic param: " + sub);

        try {
            con = Nats.connect(normalizedOptions());
            js = con.jetStream();
        } catch (Exception e) {
            log.error("createProducer exception " + e);
            return null;
        }
        log.info("createProducer done");
        return CompletableFuture.completedFuture(new NatsBenchmarkProducer(con, sub, js));
    }

    @Override public CompletableFuture<BenchmarkConsumer> createConsumer(String sub, String subscriptionName,
        ConsumerCallback consumerCallback) {

        if (config.jsConsumerMode.equalsIgnoreCase("push")) {
            return createPushConsumer(sub, subscriptionName, consumerCallback);
        } else { // "pull"
            return createPullConsumer(sub, subscriptionName, consumerCallback);
        }
    }

    CompletableFuture<BenchmarkConsumer> createPushConsumer(String sub, String subscriptionName,
                                                            ConsumerCallback consumerCallback) {
        Connection con;
        JetStream js;
        Dispatcher conDispatcher;

        log.info("createConsumer (push)");
        log.info("topic param: " + sub);
        log.info("subscriptionName param: " + subscriptionName);

        try {
            con = Nats.connect(normalizedOptions());
            js = con.jetStream();

            conDispatcher = con.createDispatcher();

            MessageHandler mh = msg -> {
                msg.ack();
                consumerCallback.messageReceived(msg.getData(),
                        Long.parseLong(msg.getHeaders().getFirst("pubstamp")));
            };

            PushSubscribeOptions so = PushSubscribeOptions.builder()
                    .stream("Str-" + sub)
                    .durable("Con-" + sub)
                    .deliverGroup("Grp-" + sub)
                    .build();

            // AutoAck false
            js.subscribe(sub, conDispatcher, mh, false, so);

        } catch (Exception e) {
            log.error("createConsumer exception " + e);
            return null;
        }
        log.info("createConsumer done");
        return CompletableFuture.completedFuture(new NatsBenchmarkConsumer(con, js));
    }

    CompletableFuture<BenchmarkConsumer> createPullConsumer(String sub, String subscriptionName,
                                                            ConsumerCallback consumerCallback) {
        Connection con;
        JetStream js;
        NatsBenchmarkConsumer nbc;

        log.info("createConsumer (pull)");
        log.info("topic param: " + sub);
        log.info("subscriptionName param: " + subscriptionName);

        try {
            con = Nats.connect(normalizedOptions());
            js = con.jetStream();
            nbc = new NatsBenchmarkConsumer(con, js);

            PullSubscribeOptions so = PullSubscribeOptions.builder()
                    .stream("Str-" + sub)
                    .durable("Con-" + sub)
                    .build();

            JetStreamSubscription jss = js.subscribe(sub, so);

            nbc.startPolling(jss, consumerCallback);

        } catch (Exception e) {
            log.error("createConsumer exception " + e);
            return null;
        }
        log.info("createConsumer done");

        return CompletableFuture.completedFuture(nbc);
    }



    @Override public void close() throws Exception {
        // TODO: Cleanup file-based JetStreams and JS Consumers
    }

    private String patchUserPass(String baseUrl) {
        // monkey patch this for a benchmark
        // assumes nats://
        int index = 7;
        String pre = baseUrl.substring(0,index);
        String post = baseUrl.substring(index);
        return pre + "UserA:s3cr3t@" + post;
    }

    private Options normalizedOptions() {
        Options.Builder builder = new Options.Builder();
        builder.maxReconnects(5);
        builder.errorListener(new NatsBenchmarkErrorListener());
        for(int i=0; i < config.workers.length; i++) {
            builder.server(patchUserPass(config.workers[i]));
        }
        return builder.build();
    }

    private static final Logger log = LoggerFactory.getLogger(NatsBenchmarkDriver.class);
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

}
