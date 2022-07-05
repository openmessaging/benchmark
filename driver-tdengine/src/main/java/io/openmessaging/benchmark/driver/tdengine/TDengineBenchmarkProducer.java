package io.openmessaging.benchmark.driver.tdengine;

import io.openmessaging.benchmark.driver.BenchmarkProducer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class TDengineBenchmarkProducer implements BenchmarkProducer {
    @Override
    public CompletableFuture<Void> sendAsync(Optional<String> key, byte[] payload) {
        return null;
    }

    @Override
    public void close() throws Exception {

    }
}
