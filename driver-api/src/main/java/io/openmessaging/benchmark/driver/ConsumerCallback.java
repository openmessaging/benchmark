package io.openmessaging.benchmark.driver;

public interface ConsumerCallback {
	void messageReceived(byte[] payload);
}
