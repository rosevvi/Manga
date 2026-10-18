package com.manga.repository;

import com.manga.entity.CreativeEventOutbox;
import com.manga.mapper.CreativeEventOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 封装 Creative Event Outbox 的持久化访问。
 */
@Repository
@RequiredArgsConstructor
public class CreativeEventOutboxRepository {

    private final CreativeEventOutboxMapper mapper;

    /** 新增事件投递记录。 */
    public CreativeEventOutbox create(CreativeEventOutbox outbox) {
        mapper.insert(outbox);
        return outbox;
    }

    /** 查询有限数量的可领取记录。 */
    public List<CreativeEventOutbox> findClaimCandidates(LocalDateTime now, int limit) {
        return mapper.findClaimCandidates(now, limit);
    }

    /** 原子领取事件投递记录。 */
    public boolean claim(long id, String owner, LocalDateTime claimUntil, LocalDateTime now, String actor) {
        return mapper.claim(id, owner, claimUntil, now, actor) == 1;
    }

    /** 将当前实例领取的记录标记为已发布。 */
    public boolean markPublished(long id, String owner, LocalDateTime now, String actor) {
        return mapper.markPublished(id, owner, now, actor) == 1;
    }

    /** 将当前实例领取失败的记录退回等待状态。 */
    public boolean markRetry(long id, String owner, LocalDateTime nextAttemptAt, String error, String actor) {
        return mapper.markRetry(id, owner, nextAttemptAt, error, actor) == 1;
    }
}
