package com.suwasarana.api.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Makes sure a scheduled job runs on one application instance at a time.
 *
 * <p>On PostgreSQL it takes a session-level advisory lock ({@code pg_try_advisory_lock}) on a key derived from the job
 * name, on a connection that is held for the duration of the job, and releases it afterwards; an instance that does
 * not get the lock simply skips this run. On any other database (the H2 used by the tests, a single-node setup) the
 * job just runs. Disable with {@code app.scheduler.lock-enabled=false}.
 *
 * <p>Other schedulers (for example the escalation job in the matching package) can call the same guard.
 */
@Component
public class SchedulerGuard {

    private static final Logger log = LoggerFactory.getLogger(SchedulerGuard.class);

    private final DataSource dataSource;
    private final boolean enabled;

    @Autowired
    public SchedulerGuard(DataSource dataSource, @Value("${app.scheduler.lock-enabled:true}") boolean enabled) {
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    /**
     * Runs {@code work} unless another instance is currently running the job with the same name.
     *
     * @return {@code true} if the work ran (to completion), {@code false} if it was skipped
     */
    public boolean runExclusively(String jobName, Runnable work) {
        if (!enabled) {
            work.run();
            return true;
        }
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            log.error("Scheduled job '{}' skipped: no database connection for the lock ({})", jobName, e.getClass().getSimpleName());
            return false;
        }
        try {
            if (!isPostgres(connection)) {
                work.run();
                return true;
            }
            long key = lockKey(jobName);
            if (!tryLock(connection, key)) {
                log.debug("Scheduled job '{}' skipped: another instance holds the lock", jobName);
                return false;
            }
            try {
                work.run();
                return true;
            } finally {
                unlock(connection, key, jobName);
            }
        } catch (SQLException e) {
            log.error("Scheduled job '{}' skipped: lock handling failed ({})", jobName, e.getClass().getSimpleName());
            return false;
        } finally {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // nothing sensible to do
            }
        }
    }

    private static boolean isPostgres(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        return product != null && product.toLowerCase().contains("postgres");
    }

    private static boolean tryLock(Connection connection, long key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static void unlock(Connection connection, long key, String jobName) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            ps.setLong(1, key);
            ps.execute();
        } catch (SQLException e) {
            log.error("Could not release the advisory lock of job '{}'; releasing all locks of this session", jobName);
            try (PreparedStatement all = connection.prepareStatement("SELECT pg_advisory_unlock_all()")) {
                all.execute();
            } catch (SQLException ignored) {
                // the pooled connection is probably broken and will be discarded
            }
        }
    }

    /** Stable 64-bit key per job name. */
    static long lockKey(String jobName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(("suwa-sarana:job:" + jobName).getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest, 0, 8).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
