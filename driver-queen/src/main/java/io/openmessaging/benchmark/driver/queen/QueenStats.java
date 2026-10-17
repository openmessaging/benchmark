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
package io.openmessaging.benchmark.driver.queen;


import java.util.concurrent.atomic.LongAdder;

/** The driver's own counters, logged when it closes: a cross-check of OMB's numbers. */
final class QueenStats {
    final LongAdder pushes = new LongAdder();
    final LongAdder pushFailures = new LongAdder();
    final LongAdder messagesPushed = new LongAdder();
    final LongAdder messagesPushFailed = new LongAdder();
    final LongAdder pushGroups = new LongAdder();
    final LongAdder bufferFull = new LongAdder();

    final LongAdder pops = new LongAdder();
    final LongAdder emptyPops = new LongAdder();
    final LongAdder popErrors = new LongAdder();
    final LongAdder jsonAnswers = new LongAdder();
    final LongAdder messagesDelivered = new LongAdder();

    final LongAdder acks = new LongAdder();
    final LongAdder ackFailures = new LongAdder();
    final LongAdder messagesAcked = new LongAdder();
    final LongAdder acksRefused = new LongAdder();
    final LongAdder renewals = new LongAdder();
    final LongAdder renewFailures = new LongAdder();
    final LongAdder popsAfterClose = new LongAdder();
    final LongAdder doorbells = new LongAdder();
    final LongAdder doorbellRings = new LongAdder();
    final LongAdder doorbellErrors = new LongAdder();

    @Override
    public String toString() {
        return String.format(
                "pushes %d (failed %d, %.1f groups each), messages pushed %d (failed %d),"
                        + " %d failed at once on a full buffer;"
                        + " pops %d (empty %d, errors %d, JSON answers %d, after close %d),"
                        + " messages delivered %d; acks %d (failed %d), messages acked %d (refused %d);"
                        + " lease extensions %d (failed %d); doorbell fetches %d (found data %d, failed %d)",
                pushes.sum(),
                pushFailures.sum(),
                pushes.sum() == 0 ? 0.0 : (double) pushGroups.sum() / pushes.sum(),
                messagesPushed.sum(),
                messagesPushFailed.sum(),
                bufferFull.sum(),
                pops.sum(),
                emptyPops.sum(),
                popErrors.sum(),
                jsonAnswers.sum(),
                popsAfterClose.sum(),
                messagesDelivered.sum(),
                acks.sum(),
                ackFailures.sum(),
                messagesAcked.sum(),
                acksRefused.sum(),
                renewals.sum(),
                renewFailures.sum(),
                doorbells.sum(),
                doorbellRings.sum(),
                doorbellErrors.sum());
    }
}
