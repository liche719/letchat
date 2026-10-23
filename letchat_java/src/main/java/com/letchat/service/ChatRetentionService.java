package com.letchat.service;

/**
 * 消息可靠性表的保留策略。
 *
 * <p>outbox 与 inbox 只写不删会持续膨胀：outbox 的 claim 查询成本随行数上升，
 * inbox 也会长期占用唯一键索引。这里按保留天数分批清理已完结的数据。
 */
public interface ChatRetentionService {

    /**
     * 清理已发布/已失败（终态）的 outbox 记录与已消费的 inbox 记录。
     *
     * @param retentionDays 保留天数，早于该天数的终态数据会被删除
     * @return 本次实际删除的行数
     */
    PurgeResult purge(int retentionDays);

    record PurgeResult(int outboxDeleted, int inboxDeleted) {
    }
}
