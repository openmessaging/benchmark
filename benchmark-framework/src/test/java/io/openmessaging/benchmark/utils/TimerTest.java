package io.openmessaging.benchmark.utils;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class TimerTest {

    @Test
    void elapsedMillis() {
        Supplier<Long> mockClock = mock(Supplier.class);
        when(mockClock.get()).thenReturn(MILLISECONDS.toNanos(1), MILLISECONDS.toNanos(3));
        Timer timer = new Timer(mockClock);
        assertThat(timer.elapsedMillis()).isEqualTo(2.0d);
    }

    @Test
    void elapsedSeconds() {
        Supplier<Long> mockClock = mock(Supplier.class);
        when(mockClock.get()).thenReturn(SECONDS.toNanos(1), SECONDS.toNanos(3));
        Timer timer = new Timer(mockClock);
        assertThat(timer.elapsedSeconds()).isEqualTo(2.0d);
    }
}