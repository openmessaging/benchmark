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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsBenchmarkErrorListener implements ErrorListener {
    public void errorOccurred(Connection conn, String error)
    {
        log.info("The server notified the client with: " + error);
    }

    public void exceptionOccurred(Connection conn, Exception exp) {
        log.info("The connection handled an exception: " + exp.getLocalizedMessage());
    }

    // Detect slow consumers
    public void slowConsumerDetected(Connection conn, Consumer consumer) {
        // Get the dropped count
        log.info("A slow consumer dropped messages: "+ consumer.getDroppedCount());
    }

    private static final Logger log = LoggerFactory.getLogger(NatsBenchmarkErrorListener.class);
}


