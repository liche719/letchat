package com.letchat.service.impl;

import com.letchat.mappers.ChatMessageInboxMapper;
import com.letchat.mappers.ChatMessageOutboxMapper;
import com.letchat.service.ChatRetentionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.function.IntFunction;

@Service("chatRetentionService")
@Slf4j
public class ChatRetentionServiceImpl implements ChatRetentionService {

    /** 单条 DELETE 的上限，避免一次锁住大量行。 */
    static final int DELETE_BATCH_SIZE = 5000;
    /** 单次调度最多清理的批次数，避免清理任务长期占用一个数据库连接。 */
    static final int MAX_BATCHES_PER_RUN = 20;

    @Resource
    private ChatMessageOutboxMapper chatMessageOutboxMapper;

    @Resource
    private ChatMessageInboxMapper chatMessageInboxMapper;

    @Value("${letchat.retention.days:7}")
    private int retentionDays = 7;

    @Scheduled(cron = "${letchat.retention.cron:0 30 3 * * *}")
    public void purgeDaily() {
        PurgeResult result = purge(retentionDays);
        if (result.outboxDeleted() > 0 || result.inboxDeleted() > 0) {
            log.info("清理消息可靠性表完成，保留 {} 天：outbox={}, inbox={}",
                    retentionDays, result.outboxDeleted(), result.inboxDeleted());
        }
    }

    @Override
    public PurgeResult purge(int days) {
        int outboxDeleted = purgeInBatches(days, retention ->
                chatMessageOutboxMapper.deleteFinishedBefore(retention, DELETE_BATCH_SIZE));
        int inboxDeleted = purgeInBatches(days, retention ->
                chatMessageInboxMapper.deleteConsumedBefore(retention, DELETE_BATCH_SIZE));
        return new PurgeResult(outboxDeleted, inboxDeleted);
    }

    private int purgeInBatches(int days, IntFunction<Integer> deleteBatch) {
        int deleted = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int affected = deleteBatch.apply(days);
            deleted += affected;
            if (affected < DELETE_BATCH_SIZE) {
                break;
            }
        }
        return deleted;
    }
}
