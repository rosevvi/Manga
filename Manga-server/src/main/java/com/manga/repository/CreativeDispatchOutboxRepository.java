package com.manga.repository;

import com.manga.entity.CreativeDispatchOutbox;
import com.manga.mapper.CreativeDispatchOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 封装 Dispatch Outbox 的持久化和竞争领取。
 */
@Repository
@RequiredArgsConstructor
public class CreativeDispatchOutboxRepository {

    private final CreativeDispatchOutboxMapper mapper;

    /** 新建一条调度投递记录。 */
    public CreativeDispatchOutbox create(CreativeDispatchOutbox outbox) {
        mapper.insert(outbox);
        return outbox;
    }

    /** 查询有限数量的可领取记录。 */
    public List<CreativeDispatchOutbox> findClaimCandidates(LocalDateTime now, int limit) {
        return mapper.findClaimCandidates(now, limit);
    }

    /** 原子领取调度记录。 */
    public boolean claim(long id, String owner, LocalDateTime claimUntil, LocalDateTime now, String actor) {
        return mapper.claim(id, owner, claimUntil, now, actor) == 1;
    }

    /** 标记当前实例领取的记录已经投递。 */
    public boolean markDispatched(long id, String owner, LocalDateTime now, String actor) {
        return mapper.markDispatched(id, owner, now, actor) == 1;
    }

    /** 退回当前实例领取失败的记录。 */
    public boolean markRetry(long id, String owner, LocalDateTime nextAttemptAt, String error, String actor) {
        return mapper.markRetry(id, owner, nextAttemptAt, error, actor) == 1;
    }
}
