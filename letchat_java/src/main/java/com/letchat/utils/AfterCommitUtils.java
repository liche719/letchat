package com.letchat.utils;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 把「提交后才允许发生」的副作用（WebSocket 推送、缓存刷新）延后到事务提交之后。
 *
 * <p>事务内直接推送的问题是：一旦后续逻辑抛错回滚，客户端已经收到消息，
 * 出现「客户端看得到、数据库查不到」的幽灵消息。这个工具保证：
 * <ul>
 *   <li>处于事务中：注册 afterCommit 回调，回滚则不执行；</li>
 *   <li>没有事务（含单元测试直接调 service）：立即执行，行为不变。</li>
 * </ul>
 */
public final class AfterCommitUtils {

    private AfterCommitUtils() {
    }

    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
