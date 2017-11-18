package io.openmessaging.benchmark;

import com.google.common.primitives.Longs;
import io.openmessaging.benchmark.driver.kafka.KafkaBenchmarkProducer;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class TestMain {
    public static void main(String[] args) {
        StringBuilder str = new StringBuilder();
        char[] alphabet = "abcdefghijklmnopqrstuvwxyz".toCharArray();
        int sizeBytes = 1024 * 1024 * 10;
        // Message number + producer hashcode + trim last byte
        int overhead = 8 + 4 + 8;
        int sizeMinusOverhead = sizeBytes - overhead;
        for (int i = 0; i < sizeMinusOverhead; i++) {
            str.append(alphabet[i%25]);
            //System.out.println(str);
        }
        byte[] message = str.toString().getBytes();

        Long createTime = System.nanoTime();
        final byte[] payloadData = new byte[sizeBytes];
        byte[] messageNumberBytes = Longs.toByteArray(123213L);
        for (int i = 0; i < 8; i++) {
            payloadData[i] = messageNumberBytes[i];
        }
        byte[] produceHashCodeBytes = ByteBuffer.allocate(4).putInt(new KafkaBenchmarkProducer(null, null).hashCode()).array();
        for (int i = 0; i < 4; i++) {
            payloadData[i+8] = produceHashCodeBytes[i];
        }
        byte[] produceTimestampBytes = Longs.toByteArray(System.nanoTime());
        for (int i = 0; i < 8; i++) {
            payloadData[i+12] = produceTimestampBytes[i];
        }
        for (int i = 0; i < message.length; i++) {
            payloadData[i+20] = message[i];
        }

        System.out.println(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - createTime));
        System.out.println(Longs.toByteArray(12345L).length);
        System.out.println(payloadData.length);
        //System.out.println(new String(payloadData));


        LongAdder test = new LongAdder();
        test.increment();
        test.increment();
        test.increment();
        System.out.println(test.longValue());
        System.out.println(test.sumThenReset());
    }
}
