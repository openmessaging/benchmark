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
package io.openmessaging.benchmark.driver.redis;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.openmessaging.benchmark.driver.BenchmarkProducer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.XAddParams;

public class RedisBenchmarkProducer implements BenchmarkProducer {
    private final JedisPool pool;
    private final String rmqTopic;
    private final XAddParams xaddParams;

    public RedisBenchmarkProducer(final JedisPool pool, final String rmqTopic) {
        this.pool = pool;
        this.rmqTopic = rmqTopic;
        this.xaddParams = redis.clients.jedis.params.XAddParams.xAddParams();
    }

    @Override
    public CompletableFuture<Void> sendAsync(final Optional<String> key, final byte[] payload) {
        Map<byte[], byte[]> map1 = new HashMap<>();
        map1.put("payload".getBytes(UTF_8), payload);

        if (key.isPresent()) {
            map1.put("key".getBytes(UTF_8), key.toString().getBytes(UTF_8));
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        try (Jedis jedis = this.pool.getResource()) {
            jedis.xadd(this.rmqTopic.getBytes(UTF_8), map1, this.xaddParams);
            future.complete(null);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void close() throws Exception {
        // Close in Driver
    }
}
