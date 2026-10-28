package com.suwasarana.api.ratelimit;

import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BucketStoreTest {

    /** Manually advanced clock so refill and eviction can be tested without sleeping. */
    private static final class FakeMeter implements TimeMeter {
        final AtomicLong nanos = new AtomicLong(1_000_000_000_000L);
        @Override public long currentTimeNanos() { return nanos.get(); }
        @Override public boolean isWallClockBased() { return false; }
        void advance(Duration d) { nanos.addAndGet(d.toNanos()); }
    }

    @Test
    @DisplayName("Allows exactly the limit, then refuses with a Retry-After of at least one second")
    void allowsLimitThenRefuses() {
        FakeMeter clock = new FakeMeter();
        BucketStore store = new BucketStore(100, clock);
        for (int i = 0; i < 3; i++) {
            assertTrue(store.tryConsume("k", 3, Duration.ofMinutes(1)).allowed());
        }
        BucketStore.Outcome blocked = store.tryConsume("k", 3, Duration.ofMinutes(1));
        assertFalse(blocked.allowed());
        assertTrue(blocked.retryAfterSeconds() >= 1 && blocked.retryAfterSeconds() <= 20,
                "greedy refill of 3/min frees a token within 20 s but was " + blocked.retryAfterSeconds());
    }

    @Test
    @DisplayName("Tokens come back as time passes")
    void refills() {
        FakeMeter clock = new FakeMeter();
        BucketStore store = new BucketStore(100, clock);
        for (int i = 0; i < 2; i++) {
            store.tryConsume("k", 2, Duration.ofMinutes(1));
        }
        assertFalse(store.tryConsume("k", 2, Duration.ofMinutes(1)).allowed());
        clock.advance(Duration.ofSeconds(31));
        assertTrue(store.tryConsume("k", 2, Duration.ofMinutes(1)).allowed());
    }

    @Test
    @DisplayName("Keys are independent")
    void independentKeys() {
        BucketStore store = new BucketStore(100, new FakeMeter());
        assertTrue(store.tryConsume("a", 1, Duration.ofMinutes(1)).allowed());
        assertFalse(store.tryConsume("a", 1, Duration.ofMinutes(1)).allowed());
        assertTrue(store.tryConsume("b", 1, Duration.ofMinutes(1)).allowed());
    }

    @Test
    @DisplayName("Buckets idle for longer than their window are evicted by the sweep")
    void idleBucketsAreEvicted() {
        FakeMeter clock = new FakeMeter();
        BucketStore store = new BucketStore(1000, clock);
        for (int i = 0; i < 50; i++) {
            store.tryConsume("ip|" + i, 5, Duration.ofMinutes(1));
        }
        store.tryConsume("long", 5, Duration.ofHours(1));
        assertEquals(51, store.size());

        clock.advance(Duration.ofMinutes(2));
        // next access past the sweep interval triggers the sweep
        store.tryConsume("trigger", 5, Duration.ofMinutes(1));
        assertEquals(2, store.size(), "only the 1-hour bucket and the new one are still needed");
    }

    @Test
    @DisplayName("The number of buckets never grows past the configured cap")
    void sizeIsBounded() {
        BucketStore store = new BucketStore(100, new FakeMeter());
        for (int i = 0; i < 10_000; i++) {
            store.tryConsume("flood|" + i, 5, Duration.ofHours(1));
            assertTrue(store.size() <= 100, "size " + store.size() + " exceeded the cap at i=" + i);
        }
    }

    @Test
    @DisplayName("Eviction drops the least recently used buckets first")
    void leastRecentlyUsedGoFirst() {
        FakeMeter clock = new FakeMeter();
        BucketStore store = new BucketStore(20, clock);
        for (int i = 0; i < 20; i++) {
            clock.advance(Duration.ofMillis(10));
            store.tryConsume("k" + i, 1, Duration.ofHours(1));
        }
        // k0 is the oldest; touching it makes k1 the oldest
        clock.advance(Duration.ofMillis(10));
        assertFalse(store.tryConsume("k0", 1, Duration.ofHours(1)).allowed());
        clock.advance(Duration.ofMillis(10));
        store.tryConsume("new", 1, Duration.ofHours(1)); // overflow: evicts k1, k2
        // k0 survived (still exhausted); k1 was evicted so it starts fresh
        assertFalse(store.tryConsume("k0", 1, Duration.ofHours(1)).allowed());
        assertTrue(store.tryConsume("k1", 1, Duration.ofHours(1)).allowed());
    }
}
