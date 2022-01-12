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

import io.nats.client.*;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NatsBenchmarkConsumer implements BenchmarkConsumer {
    // the con has-a dispatcher, the dispatcher has-a subscription
    private Connection con;
    private JetStream js;

    private ExecutorService executor
            = Executors.newSingleThreadExecutor();

    public void startPolling(JetStreamSubscription jss,
                              ConsumerCallback consumerCallback) {

        executor.submit(() -> {
            while (true) {
                Iterator<Message> iter = jss.iterate(1000, Duration.ofSeconds(1));
                while (iter.hasNext()) {
                    Message m = iter.next();
                    // process message
                    consumerCallback.messageReceived(m.getData(), Long.parseLong(m.getHeaders().getFirst("pubstamp")));
                    m.ack();
                }
            }
        });

        return;
    }

    public NatsBenchmarkConsumer (Connection con, JetStream js) {
        this.con = con;
        this.js = js;
    }

    @Override public void close() throws Exception {
        con.drain(Duration.ofSeconds(10));
        con.close();
    }


    private static final Logger log = LoggerFactory.getLogger(NatsBenchmarkConsumer.class);

}
