package io.nats.client.impl;

import io.nats.client.support.ByteArrayBuilder;

public class NatsJsBenchmarkMessage extends NatsMessage {

    public static final String HDR_PUB_TIME = "pt";

    public NatsJsBenchmarkMessage(String subject, byte[] data) {
        super(subject, null, data);
        headers = new Headers();
        update();
    }

    private void update() {
        headers.put(HDR_PUB_TIME, "" + System.currentTimeMillis());
        protocolBabDirty = true;
    }

    @Override
    ByteArrayBuilder getProtocol() {
        update();
        return super.getProtocol();
    }
}
