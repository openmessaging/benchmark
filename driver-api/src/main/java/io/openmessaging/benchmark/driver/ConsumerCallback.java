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
package io.openmessaging.benchmark.driver;


import java.nio.ByteBuffer;

/** Callback that the driver implementation calls when a message is received. */
public interface ConsumerCallback {
    /**
     * Driver should invoke this method (or the ByteBuffer variant) once for each message received.
     *
     * @param payload the received message payload
     * @param publishTimestamp the publish timestamp in milliseconds
     */
    void messageReceived(byte[] payload, long publishTimestamp);

    /**
     * Driver should invoke this method (or the byte[] variant) once for each message received.
     *
     * @param payload the received message payload
     * @param publishTimestamp the publish timestamp in milliseconds
     */
    void messageReceived(ByteBuffer payload, long publishTimestamp);
}
