/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openmessaging.benchmark.utils;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/**
 * Provides a next operation time for rate limited operation streams.<br>
 * The rate limiter is thread safe and can be shared by all threads.
 */
public final class UniformRateLimiter {

    private static final AtomicLongFieldUpdater<UniformRateLimiter> V_TIME_UPDATER =
            AtomicLongFieldUpdater.newUpdater(UniformRateLimiter.class, "virtualTime");
    private static final AtomicLongFieldUpdater<UniformRateLimiter> START_UPDATER =
            AtomicLongFieldUpdater.newUpdater(UniformRateLimiter.class, "start");
    private static final double ONE_SEC_IN_NS = SECONDS.toNanos(1);
    private volatile long start = Long.MIN_VALUE;
    private volatile long virtualTime;
    private final double opsPerSec;
    private final long intervalNs;
    private final Supplier<Long> nanoClock;

    UniformRateLimiter(final double opsPerSec, Supplier<Long> nanoClock) {
        if (Double.isNaN(opsPerSec) || Double.isInfinite(opsPerSec)) {
            throw new IllegalArgumentException("opsPerSec cannot be Nan or Infinite");
        }
        if (opsPerSec <= 0) {
            throw new IllegalArgumentException("opsPerSec must be greater then 0");
        }
        this.opsPerSec = opsPerSec;
        intervalNs = Math.round(ONE_SEC_IN_NS / opsPerSec);
        this.nanoClock = nanoClock;
    }

    public UniformRateLimiter(final double opsPerSec) {
        this(opsPerSec, System::nanoTime);
    }

    public double getOpsPerSec() {
        return opsPerSec;
    }

    public long getIntervalNs() {
        return intervalNs;
    }

    public long acquire() {
        final long currOpIndex = V_TIME_UPDATER.getAndIncrement(this);
        long start = this.start;
        if (start == Long.MIN_VALUE) {
            start = nanoClock.get();
            if (!START_UPDATER.compareAndSet(this, Long.MIN_VALUE, start)) {
                start = this.start;
                assert start != Long.MIN_VALUE;
            }
        }
        return start + currOpIndex * intervalNs;
    }

    public static void uninterruptibleSleepNs(final long intendedTime) {
        long sleepNs;
        while ((sleepNs = (intendedTime - System.nanoTime())) > 0) {
            LockSupport.parkNanos(sleepNs);
        }
    }
}
