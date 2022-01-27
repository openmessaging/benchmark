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
package io.openmessaging.benchmark.driver.natsjs;

import io.nats.client.*;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nats.client.impl.NatsJsBenchmarkMessage.HDR_PUB_TIME;

public class NatsJsBenchmarkConsumer implements BenchmarkConsumer {
    // provided
    private final String subject;
    private final String subscriptionName;
    private final ConsumerCallback consumerCallback;

    // internal
    private final AtomicInteger received;
    private final JetStream js;

    // config settings
    private final int ackInterval;
    private final long maxAckPending;
    private final long fcHeartbeat;
    private final String deliverGroup;

    // subscription stuff
    private final Dispatcher dispatcher;
    private final JetStreamSubscription sub;

    public NatsJsBenchmarkConsumer(NatsJsConfig config, Connection conn, String subject, String subscriptionName, ConsumerCallback consumerCallback) throws IOException, JetStreamApiException {
        this.subject = subject;
        this.subscriptionName = subscriptionName;
        this.consumerCallback = consumerCallback;

        received = new AtomicInteger();
        ackInterval = config.jsConsumerAckInterval < 1 ? 100 : config.jsConsumerAckInterval;
        maxAckPending = config.jsConsumerMaxAckPending < 1 ? 300 : config.jsConsumerMaxAckPending;
        fcHeartbeat = config.jsConsumerFlowControlHeartbeatTime < 1 ? 5000 : config.jsConsumerFlowControlHeartbeatTime;
        deliverGroup = config.jsDeliverGroup;

        js = conn.jetStream(); // maybe will use config later?

        CreateSubResult cr = createPushSub(conn);
        dispatcher = cr.dispatcher;
        sub = cr.sub;
    }

    private static class CreateSubResult { // use this so instance vars can be final
        Dispatcher dispatcher;
        JetStreamSubscription sub;
    }

    private CreateSubResult createPushSub(Connection conn) throws IOException, JetStreamApiException {
        CreateSubResult cr = new CreateSubResult();
        cr.dispatcher = conn.createDispatcher();

        MessageHandler mh = msg -> {
            try {

                long publishTimestamp = Long.parseLong(msg.getHeaders().getFirst(HDR_PUB_TIME));
                consumerCallback.messageReceived(msg.getData(), publishTimestamp);
                if (received.incrementAndGet() % ackInterval == 0) {
                    msg.ack();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        AckPolicy ap = ackInterval == 1 ? AckPolicy.Explicit : (ackInterval > 1 ? AckPolicy.All : AckPolicy.None);

        PushSubscribeOptions pso = ConsumerConfiguration.builder()
            .ackPolicy(ap)
            .maxAckPending(maxAckPending)
            .flowControl(fcHeartbeat)
            .deliverGroup(deliverGroup)
            .buildPushSubscribeOptions();
        cr.sub = js.subscribe(subject, dispatcher, mh, false, pso);

        return cr;
    }

    @Override
    public void close() throws Exception {
        try {
            if (sub != null) {
                if (dispatcher == null) {
                    sub.unsubscribe();
                }
                else {
                    dispatcher.unsubscribe(sub);
                }
            }
        }
        catch (Exception ignore) {}
    }
}
