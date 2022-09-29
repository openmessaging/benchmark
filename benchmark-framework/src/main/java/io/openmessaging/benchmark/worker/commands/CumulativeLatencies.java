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
package io.openmessaging.benchmark.worker.commands;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.HdrHistogram.Histogram;

public class CumulativeLatencies {

    public Histogram publishLatency = new Histogram(SECONDS.toMicros(60), 5);
    public Histogram publishDelayLatency = new Histogram(SECONDS.toMicros(60), 5);
    public Histogram endToEndLatency = new Histogram(HOURS.toMicros(12), 5);

    public CumulativeLatencies plus(CumulativeLatencies toAdd) {
        CumulativeLatencies result = new CumulativeLatencies();

        result.publishLatency.add(this.publishLatency);
        result.publishDelayLatency.add(this.publishDelayLatency);
        result.endToEndLatency.add(this.endToEndLatency);

        result.publishLatency.add(toAdd.publishLatency);
        result.publishDelayLatency.add(toAdd.publishDelayLatency);
        result.endToEndLatency.add(toAdd.endToEndLatency);

        return result;
    }
}
