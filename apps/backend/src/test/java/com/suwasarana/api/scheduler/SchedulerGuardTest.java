package com.suwasarana.api.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulerGuardTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    private final PreparedStatement tryLock = mock(PreparedStatement.class);
    private final PreparedStatement unlock = mock(PreparedStatement.class);
    private final ResultSet lockResult = mock(ResultSet.class);

    private SchedulerGuard postgresGuard(boolean lockAvailable) throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.prepareStatement(contains("pg_try_advisory_lock"))).thenReturn(tryLock);
        when(connection.prepareStatement(contains("pg_advisory_unlock(?)"))).thenReturn(unlock);
        when(tryLock.executeQuery()).thenReturn(lockResult);
        when(lockResult.next()).thenReturn(true);
        when(lockResult.getBoolean(1)).thenReturn(lockAvailable);
        return new SchedulerGuard(dataSource, true);
    }

    @Test
    @DisplayName("PostgreSQL: with the advisory lock the job runs, then the lock is released and the connection returned")
    void runsAndUnlocks() throws SQLException {
        SchedulerGuard guard = postgresGuard(true);
        AtomicInteger runs = new AtomicInteger();

        assertTrue(guard.runExclusively("job-a", runs::incrementAndGet));

        assertEquals(1, runs.get());
        verify(tryLock).setLong(org.mockito.ArgumentMatchers.eq(1), anyLong());
        verify(unlock).execute();
        verify(connection).close();
    }

    @Test
    @DisplayName("PostgreSQL: when another instance holds the lock the run is skipped and nothing is unlocked")
    void skipsWhenLocked() throws SQLException {
        SchedulerGuard guard = postgresGuard(false);
        AtomicInteger runs = new AtomicInteger();

        assertFalse(guard.runExclusively("job-a", runs::incrementAndGet));

        assertEquals(0, runs.get());
        verify(unlock, never()).execute();
        verify(connection).close();
    }

    @Test
    @DisplayName("PostgreSQL: the lock is released even when the job throws")
    void unlocksAfterFailure() throws SQLException {
        SchedulerGuard guard = postgresGuard(true);
        assertThrows(IllegalStateException.class, () -> guard.runExclusively("job-a", () -> { throw new IllegalStateException("boom"); }));
        verify(unlock).execute();
        verify(connection).close();
    }

    @Test
    @DisplayName("Other databases (H2, single node): the job just runs, no lock statements")
    void nonPostgresRunsDirectly() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");
        AtomicInteger runs = new AtomicInteger();

        assertTrue(new SchedulerGuard(dataSource, true).runExclusively("job-a", runs::incrementAndGet));

        assertEquals(1, runs.get());
        verify(connection, never()).prepareStatement(contains("advisory"));
    }

    @Test
    @DisplayName("No database connection: the run is skipped rather than risking a double run")
    void noConnectionSkips() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("down"));
        AtomicInteger runs = new AtomicInteger();
        assertFalse(new SchedulerGuard(dataSource, true).runExclusively("job-a", runs::incrementAndGet));
        assertEquals(0, runs.get());
    }

    @Test
    @DisplayName("Disabled guard runs without touching the database")
    void disabled() throws SQLException {
        AtomicInteger runs = new AtomicInteger();
        assertTrue(new SchedulerGuard(dataSource, false).runExclusively("job-a", runs::incrementAndGet));
        assertEquals(1, runs.get());
        verify(dataSource, never()).getConnection();
    }

    @Test
    @DisplayName("Every job name gets its own, stable lock key")
    void lockKeys() {
        assertEquals(SchedulerGuard.lockKey("slot-reminder-24h"), SchedulerGuard.lockKey("slot-reminder-24h"));
        assertNotEquals(SchedulerGuard.lockKey("slot-reminder-24h"), SchedulerGuard.lockKey("slot-reminder-2h"));
    }
}
