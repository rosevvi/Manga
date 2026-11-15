package com.manga.repository;

import com.manga.entity.CreativeMessageProjection;
import com.manga.mapper.CreativeMessageProjectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import com.manga.agent.domain.CreativeMemoryConstants;

/** 对外只提供检查点操作，事务边界由消息投影服务控制。 */
@Repository
@RequiredArgsConstructor
public class CreativeMessageProjectionRepository {
    private final CreativeMessageProjectionMapper mapper;
    /** 新 Run 受理事务内创建检查点，已有数据由迁移回填。 */
    public void create(String runId, String actor) { mapper.ensure(runId, actor); }
    /** 锁定既有检查点，不在投影事务开头锁定所属 Run。 */
    public CreativeMessageProjection lock(String runId, String actor) {
        return mapper.lock(runId);
    }
    /** 拒绝其他尚在租约内的领取者。 */
    public boolean claim(String runId, String owner, LocalDateTime now, LocalDateTime until) {
        return mapper.claim(runId, owner, now, until) == 1;
    }
    /** 消息与水位同事务推进，拒绝所有权已改变的提交。 */
    public void advance(String runId, long sequence, String owner) {
        if (mapper.advance(runId, sequence, owner) != 1) {
            throw new IllegalStateException(CreativeMemoryConstants.PROJECTION_GAP);
        }
    }
}
