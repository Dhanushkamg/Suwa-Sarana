package com.suwasarana.api.slot;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.common.ErrorCodes;
import com.suwasarana.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Over-booking protection on the REAL database: 20 donors book the same slot (capacity 5) at the same instant. The
 * pessimistic row lock ({@code SELECT ... FOR UPDATE}) in {@link SlotBookingService#bookSlot} must let exactly 5 of them
 * through - H2 cannot prove this because it does not implement the same locking behaviour.
 */
class SlotBookingConcurrencyPostgresTest extends PostgresIntegrationTest {

    private static final int DONORS = 20;
    private static final int CAPACITY = 5;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SlotBookingService bookingService;

    @Test
    @DisplayName("20 parallel bookings of a slot with capacity 5: exactly 5 succeed, bookedCount == 5, slot FULL, 15 get slot.full")
    void parallelBookingsNeverOverbook() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Long hospital = jdbc.queryForObject("INSERT INTO users (email, phone_number, password_hash, role, verification_status, first_name, last_name) "
                + "VALUES (?, ?, 'x', 'HOSPITAL_REQUESTER', 'VERIFIED', 'Conc', 'Hospital') RETURNING id", Long.class,
                "conc.hospital." + tag + "@example.org", uniquePhone());
        Long slotId = jdbc.queryForObject("INSERT INTO donation_slots (hospital_id, start_time, end_time, capacity) "
                + "VALUES (?, now() + INTERVAL '3 days', now() + INTERVAL '3 days 1 hour', ?) RETURNING id", Long.class, hospital, CAPACITY);

        List<Long> donorUserIds = new ArrayList<>();
        for (int i = 0; i < DONORS; i++) {
            Long userId = jdbc.queryForObject("INSERT INTO users (email, phone_number, password_hash, role, verification_status, first_name, last_name) "
                    + "VALUES (?, ?, 'x', 'DONOR', 'PENDING', 'Conc', ?) RETURNING id", Long.class,
                    "conc.donor." + tag + "." + i + "@example.org", uniquePhone(), "Donor" + i);
            jdbc.update("INSERT INTO donor_profiles (user_id, blood_type, date_of_birth, weight_kg, district, latitude, longitude) "
                    + "VALUES (?, 'O+', DATE '1990-01-01', 70, 'Colombo', 6.93, 79.86)", userId);
            donorUserIds.add(userId);
        }

        ExecutorService pool = Executors.newFixedThreadPool(DONORS);
        CountDownLatch ready = new CountDownLatch(DONORS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        List<String> unexpected = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        long start;
        try {
            for (Long userId : donorUserIds) {
                Callable<Void> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        bookingService.bookSlot(slotId, userId);
                        succeeded.incrementAndGet();
                    } catch (ApiException e) {
                        if (ErrorCodes.SLOT_FULL.equals(e.getCode())) {
                            full.incrementAndGet();
                        } else {
                            unexpected.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                    } catch (Throwable t) {
                        unexpected.add(t.getClass().getSimpleName() + ": " + t.getMessage());
                    }
                    return null;
                };
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start = System.nanoTime();
            go.countDown();
            for (Future<?> f : futures) {
                f.get(120, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("CONCURRENCY-TIMING " + DONORS + " parallel bookings (capacity " + CAPACITY + ") finished in " + ms
                + " ms: succeeded=" + succeeded + " slotFull=" + full + " unexpected=" + unexpected);

        assertThat(unexpected).as("no failure other than SLOT_FULL").isEmpty();
        assertThat(succeeded.get()).isEqualTo(CAPACITY);
        assertThat(full.get()).isEqualTo(DONORS - CAPACITY);
        assertThat(jdbc.queryForObject("SELECT booked_count FROM donation_slots WHERE id = ?", Integer.class, slotId)).isEqualTo(CAPACITY);
        assertThat(jdbc.queryForObject("SELECT status FROM donation_slots WHERE id = ?", String.class, slotId)).isEqualTo("FULL");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM slot_bookings WHERE slot_id = ? AND status = 'BOOKED'", Integer.class, slotId))
                .isEqualTo(CAPACITY);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT donor_id) FROM slot_bookings WHERE slot_id = ?", Integer.class, slotId))
                .isEqualTo(CAPACITY);
    }
}
