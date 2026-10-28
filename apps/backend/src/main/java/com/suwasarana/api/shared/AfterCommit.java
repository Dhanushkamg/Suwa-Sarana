package com.suwasarana.api.shared;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Runs a side effect (SSE push, SMS) only once the surrounding transaction has committed. */
public final class AfterCommit {

    private AfterCommit() {}

    /** Runs {@code action} after commit, or immediately when no transaction is active. Exceptions are swallowed by the caller's action. */
    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
