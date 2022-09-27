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
package io.openmessaging.benchmark.driver.bookkeeper.stats;


import org.apache.bookkeeper.stats.Counter;

class CounterAdaptor implements dlshade.org.apache.bookkeeper.stats.Counter {

    private final Counter counter;

    CounterAdaptor(Counter counter) {
        this.counter = counter;
    }

    @Override
    public void clear() {
        counter.clear();
    }

    @Override
    public void inc() {
        counter.inc();
    }

    @Override
    public void dec() {
        counter.dec();
    }

    @Override
    public void add(long delta) {
        counter.add(delta);
    }

    @Override
    public Long get() {
        return counter.get();
    }
}
