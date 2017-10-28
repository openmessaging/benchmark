package com.streamlio.messaging.benchmark.driver;

import java.util.concurrent.CompletableFuture;

public interface BenchmarkProducer extends AutoCloseable {

	/**
	 * Publish a message and return a callback to track the completion of the
	 * operation.
	 * 
	 * @param message
	 * @return
	 */
	CompletableFuture<Void> sendAsync(byte[] message);

}
