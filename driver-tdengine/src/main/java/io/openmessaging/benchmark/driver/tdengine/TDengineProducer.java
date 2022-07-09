/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openmessaging.benchmark.driver.tdengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;


public class TDengineProducer {
    private static final Logger log = LoggerFactory.getLogger(TDengineProducer.class);

    private ArrayBlockingQueue<Object[]> queue = new ArrayBlockingQueue<>(10000000);
    private Thread workThread;
    private String topic;
    private boolean closing = false;
    private Config config;
    private long startNano;
    private long startTs;

    public TDengineProducer(String topic, Config config) {
        this.topic = topic;
        this.config = config;
        this.startNano = System.nanoTime();
        this.startTs = System.currentTimeMillis() * 1000000;
        this.workThread = new Thread(this::run);
        workThread.start();
    }

    public void send(byte[] payload, CompletableFuture<Void> future) throws InterruptedException {
        long ts = System.nanoTime() - startNano + startTs;
        // [ts, payload, future]
        queue.put(new Object[]{ts, new String(payload), future});
    }

    public void run() {
        Connection conn = null;
        Statement stmt = null;
        try {
            String jdbcUrl = config.jdbcURL;
            conn = DriverManager.getConnection(jdbcUrl);
            stmt = conn.createStatement();
            stmt.executeUpdate("use " + config.database);
            long tableId = System.nanoTime() + new Random().nextLong();
            tableId = Math.abs(tableId);
            String stableName = topic.replaceAll("-", "_");
            String tableName = stableName + "_" + tableId;
            String q = "create table " + tableName + " using " + stableName + " tags(" + tableId + ")";
            log.info(q);
            stmt.executeUpdate(q);
            List<String> values = new ArrayList<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            while (!closing) {
                try {
                    Object[] item = queue.poll();
                    if (item != null) {
                        Object ts = item[0];
                        Object payload = item[1];
                        values.add(" (" + ts + ",'" + payload + "')");
                        futures.add((CompletableFuture<Void>) item[3]);
                        if (values.size() == config.maxBatchSize) {
                            flush(stmt, tableName, values, futures);
                        }
                    } else {
                        if (values.size() > 0) {
                            flush(stmt, tableName, values, futures);
                        }
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (SQLException e) {
                }
            }
            if (values.size() > 0) {
                flush(stmt, tableName, values, futures);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                stmt.close();
                conn.close();
            } catch (SQLException e) {
            }
        }
    }

    private void flush(Statement stmt, String table, List<String> values, List<CompletableFuture<Void>> futures) throws SQLException {
        StringBuilder sb = new StringBuilder("insert into ").append(table).append(" values");
        for (String value : values) {
            sb.append(value);
        }
        String q = sb.toString();
        log.debug(q);
        stmt.executeUpdate(q);
        futures.forEach(f -> f.complete(null));
        values.clear();
        futures.clear();
    }

    public void close() {
        this.closing = true;
    }
}
