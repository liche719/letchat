package com.letchat.utils;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfterCommitUtilsTest {

    @Test
    void doesNotRunImmediatelyWhenTransactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            AtomicBoolean pushed = new AtomicBoolean(false);

            AfterCommitUtils.run(() -> pushed.set(true));

            // 事务还没提交，绝不能推送：否则回滚后就是幽灵消息
            assertFalse(pushed.get());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            assertTrue(pushed.get());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void skipsSideEffectsWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            AtomicBoolean pushed = new AtomicBoolean(false);

            AfterCommitUtils.run(() -> pushed.set(true));

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));

            assertFalse(pushed.get());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void runsImmediatelyWhenNoTransactionIsActive() {
        AtomicBoolean pushed = new AtomicBoolean(false);

        AfterCommitUtils.run(() -> pushed.set(true));

        assertTrue(pushed.get());
    }
}
