package io.openmessaging.benchmark.driver.tdengine;

import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TDengineBenchmarkConsumer implements BenchmarkConsumer {
    private static final Logger log = LoggerFactory.getLogger(TDengineBenchmarkConsumer.class);
    private final ExecutorService executor;
    private final Future<?> consumerTask;


    public TDengineBenchmarkConsumer(ConsumerCallback callback) {
        this.executor = Executors.newSingleThreadExecutor();
        this.consumerTask = this.executor.submit(() -> {
            for (int i = 0; i < 100; ++i) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                callback.messageReceived(new byte[100], System.currentTimeMillis());
            }
        });
    }

    @Override
    public void close() throws Exception {
        log.debug("close");
        this.consumerTask.get();
    }
}
