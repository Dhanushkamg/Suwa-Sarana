package com.suwasarana.api.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, self-evicting store of token buckets (no external cache library needed).
 *
 * <ul>
 *   <li>A bucket that has been idle for at least its own refill window is full again, so it is
 *       indistinguishable from a brand-new one and is dropped by an opportunistic sweep.</li>
 *   <li>The number of live buckets never exceeds {@code maxEntries}: when a sweep does not free enough space the
 *       least recently used 10 % are evicted.</li>
 * </ul>
 */
public class BucketStore {

    /** Result of one consumption attempt. */
    public record Outcome(boolean allowed, long retryAfterSeconds) {
        static final Outcome OK = new Outcome(true, 0);
    }

    private static final class Entry {
        final Bucket bucket;
        final long windowNanos;
        final AtomicLong lastAccess;

        Entry(Bucket bucket, long windowNanos, long now) {
            this.bucket = bucket;
            this.windowNanos = windowNanos;
            this.lastAccess = new AtomicLong(now);
        }
    }

    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();
    private final int maxEntries;
    private final TimeMeter meter;
    private final long sweepIntervalNanos;
    private final AtomicLong lastSweep;

    public BucketStore(int maxEntries) {
        this(maxEntries, TimeMeter.SYSTEM_NANOTIME);
    }

    public BucketStore(int maxEntries, TimeMeter meter) {
        this.maxEntries = Math.max(10, maxEntries);
        this.meter = meter;
        this.sweepIntervalNanos = Duration.ofSeconds(30).toNanos();
        this.lastSweep = new AtomicLong(meter.currentTimeNanos());
    }

    /** Consumes one token of the bucket {@code key} (created with {@code limit} tokens per {@code window}). */
    public Outcome tryConsume(String key, int limit, Duration window) {
        long now = meter.currentTimeNanos();
        maybeSweep(now);

        Entry entry = buckets.get(key);
        if (entry == null) {
            if (buckets.size() >= maxEntries) {
                evictLeastRecentlyUsed();
            }
            entry = buckets.computeIfAbsent(key, k -> newEntry(limit, window, now));
        }
        entry.lastAccess.set(now);

        ConsumptionProbe probe = entry.bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return Outcome.OK;
        }
        long seconds = (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L;
        return new Outcome(false, Math.max(1, seconds));
    }

    private Entry newEntry(int limit, Duration window, long now) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(limit)
                .refillGreedy(limit, window)
                .build();
        Bucket bucket = Bucket.builder()
                .withCustomTimePrecision(meter)
                .addLimit(bandwidth)
                .build();
        return new Entry(bucket, window.toNanos(), now);
    }

    private void maybeSweep(long now) {
        long last = lastSweep.get();
        if (now - last >= sweepIntervalNanos && lastSweep.compareAndSet(last, now)) {
            sweep(now);
        }
    }

    /** Removes every bucket that has been idle for at least its window. Package-visible for tests. */
    void sweep(long now) {
        buckets.entrySet().removeIf(e -> now - e.getValue().lastAccess.get() >= e.getValue().windowNanos);
    }

    private synchronized void evictLeastRecentlyUsed() {
        if (buckets.size() < maxEntries) {
            return;
        }
        sweep(meter.currentTimeNanos());
        if (buckets.size() < maxEntries) {
            return;
        }
        List<Map.Entry<String, Entry>> all = new ArrayList<>(buckets.entrySet());
        all.sort(Comparator.comparingLong(e -> e.getValue().lastAccess.get()));
        int toRemove = Math.max(1, maxEntries / 10);
        for (int i = 0; i < toRemove && i < all.size(); i++) {
            buckets.remove(all.get(i).getKey());
        }
    }

    public int size() {
        return buckets.size();
    }

    public void clear() {
        buckets.clear();
    }
}
