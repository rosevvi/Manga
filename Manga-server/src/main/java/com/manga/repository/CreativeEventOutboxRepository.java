package com.manga.repository;

import com.manga.entity.CreativeEventOutbox;
import com.manga.mapper.CreativeEventOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    /** 查询有限数量的待投递记录。 */
    public List<CreativeEventOutbox> findPending(int limit) {
        return mapper.findPending(limit);
    }

    /** 更新投递状态与审计信息。 */
    public void update(CreativeEventOutbox outbox) {
        mapper.updateById(outbox);
    }
}
