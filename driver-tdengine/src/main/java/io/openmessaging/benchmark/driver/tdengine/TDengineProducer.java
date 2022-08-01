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

import com.taosdata.jdbc.TSDBPreparedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class TDengineProducer {
    private static final Logger log = LoggerFactory.getLogger(TDengineProducer.class);

    private ArrayBlockingQueue<Object[]> queue = new ArrayBlockingQueue<>(500000);
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
        this.workThread = new Thread(this::runStmt);
        workThread.start();
    }

    public boolean send(byte[] payload, CompletableFuture<Void> future) throws InterruptedException {
        long ts = System.nanoTime() - startNano + startTs;
        // [ts, payload, future]
        return queue.offer(new Object[]{ts, new String(payload), future}, 10, TimeUnit.MILLISECONDS);
    }

    public void runStmt() {
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
            ArrayList<Long> tsBuffer = new ArrayList<>();
            ArrayList<String> payloadBuffer = new ArrayList<>();
            String psql = "INSERT INTO " + tableName + "VALUES(?, ?)";
            try (TSDBPreparedStatement pst = (TSDBPreparedStatement) conn.prepareStatement(psql)) {
                while (!closing) {
                    try {
                        Object[] item = queue.poll();
                        if (item != null) {
                            Long ts = (Long) item[0];
                            String payload = (String) item[1];
                            CompletableFuture<Void> future = (CompletableFuture<Void>) item[2];
                            // mark message sent successfully
                            future.complete(null);
                            tsBuffer.add(ts);
                            payloadBuffer.add(payload);
                            if (tsBuffer.size() == config.maxBatchSize) {
                                flushStmt(pst, tsBuffer, payloadBuffer);
                            }
                        } else {
                            Thread.sleep(3);
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } catch (SQLException e) {
                        log.info(e.getMessage());
                    }
                }
                if (tsBuffer.size() > 0) {
                    flushStmt(pst, tsBuffer, payloadBuffer);
                }

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

    public void flushStmt(TSDBPreparedStatement pst, ArrayList<Long> tsBuffer, ArrayList<String> payloadBuffer) throws SQLException {
        pst.setTimestamp(0, tsBuffer);
        pst.setString(1, payloadBuffer, config.varcharLen);
        pst.columnDataAddBatch();
        pst.columnDataExecuteBatch();
        tsBuffer.clear();
        payloadBuffer.clear();
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

            while (!closing) {
                try {
                    Object[] item = queue.poll();
                    if (item != null) {
                        Object ts = item[0];
                        Object payload = item[1];
                        CompletableFuture<Void> future = (CompletableFuture<Void>) item[2];
                        // mark message sent successfully
                        future.complete(null);
                        values.add(" (" + ts + ",'" + payload + "')");
                        if (values.size() == config.maxBatchSize) {
                            flush(stmt, tableName, values);
                        }
                    } else {
                        if (values.size() > 0) {
                            flush(stmt, tableName, values);
                        }
                        Thread.sleep(3);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (SQLException e) {
                    log.info(e.getMessage());
                }
            }
            if (values.size() > 0) {
                flush(stmt, tableName, values);
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

    private void flush(Statement stmt, String table, List<String> values) throws SQLException {
        StringBuilder sb = new StringBuilder("insert into ").append(table).append(" values");
        for (String value : values) {
            sb.append(value);
        }
        String q = sb.toString();
        values.clear();
        stmt.executeUpdate(q);
    }

    public void close() {
        this.closing = true;
    }
}
