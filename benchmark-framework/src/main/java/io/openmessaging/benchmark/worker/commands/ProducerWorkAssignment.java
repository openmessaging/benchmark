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


import io.openmessaging.benchmark.utils.distributor.KeyDistributorType;
import java.util.List;

public class ProducerWorkAssignment {

    public List<byte[]> payloadData;

    public double publishRate;

    public KeyDistributorType keyDistributorType;

    public ProducerWorkAssignment withPublishRate(double publishRate) {
        ProducerWorkAssignment copy = new ProducerWorkAssignment();
        copy.keyDistributorType = this.keyDistributorType;
        copy.payloadData = this.payloadData;
        copy.publishRate = publishRate;
        return copy;
    }
}
