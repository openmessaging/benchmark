package io.openmessaging.benchmark.driver.tdengine;

import io.openmessaging.benchmark.driver.BenchmarkProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class TDengineBenchmarkProducer implements BenchmarkProducer {
    private static final Logger log = LoggerFactory.getLogger(TDengineBenchmarkProducer.class);

    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() throws Exception {
    }
}
