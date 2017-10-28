package com.streamlio.messaging.benchmark.driver;

public interface ConsumerCallback {
	void messageReceived(byte[] payload);
}
