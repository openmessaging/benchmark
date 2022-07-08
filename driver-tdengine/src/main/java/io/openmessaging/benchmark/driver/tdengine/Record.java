package io.openmessaging.benchmark.driver.tdengine;

public class Record {
    public long ts;
    public byte[] payload;

    public Record(long ts, byte[] payload) {
        this.ts = ts;
        this.payload = payload;
    }
}
