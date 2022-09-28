package io.openmessaging.benchmark.utils;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class UniformRateLimiterTest {

    @Test
    void getOpsPerSec() {
        assertThat(new UniformRateLimiter(1000).getOpsPerSec()).isEqualTo(1000.0d);
    }

    @Test
    void getIntervalNs() {
        assertThat(new UniformRateLimiter(1000).getIntervalNs()).isEqualTo(SECONDS.toNanos(1) / 1000);
    }

    @Test
    void acquireSlowSingleThread() {
        Supplier<Long> mockClock = mock(Supplier.class);
        when(mockClock.get()).thenReturn(SECONDS.toNanos(2));
        UniformRateLimiter rateLimiter = new UniformRateLimiter(1000, mockClock);
        assertThat(rateLimiter.acquire()).isEqualTo(2000000000L);
        assertThat(rateLimiter.acquire()).isEqualTo(2001000000L);
        assertThat(rateLimiter.acquire()).isEqualTo(2002000000L);
    }

    @Test
    void uninterruptibleSleepNs() {
        long start = System.nanoTime();
        long expectedEnd = start + MILLISECONDS.toNanos(100);
        UniformRateLimiter.uninterruptibleSleepNs(expectedEnd);
        long end = System.nanoTime();
        assertThat(end).isGreaterThan(expectedEnd);
    }

    @Test
    void cinitExceptions() {
        assertThatCode(() -> new UniformRateLimiter(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new UniformRateLimiter(1.0d / 0.0d)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new UniformRateLimiter(-0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new UniformRateLimiter(0.0)).isInstanceOf(IllegalArgumentException.class);
    }
}