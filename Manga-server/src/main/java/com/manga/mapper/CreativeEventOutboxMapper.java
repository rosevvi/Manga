package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeEventOutbox;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 提供 Creative Event Outbox 的竞争领取和状态推进。
 */
public interface CreativeEventOutboxMapper extends BaseMapper<CreativeEventOutbox> {

    /** 按创建顺序查询到期或领取超时的候选记录。 */
    List<CreativeEventOutbox> findClaimCandidates(@Param("now") java.time.LocalDateTime now,
            @Param("limit") int limit);

    /** 原子领取一条候选记录。 */
    int claim(@Param("id") long id,
            @Param("claimOwner") String claimOwner,
            @Param("claimUntil") java.time.LocalDateTime claimUntil,
            @Param("now") java.time.LocalDateTime now,
            @Param("updatedBy") String updatedBy);

    /** 将当前实例领取的记录标记为已发布。 */
    int markPublished(@Param("id") long id,
            @Param("claimOwner") String claimOwner,
            @Param("publishedAt") java.time.LocalDateTime publishedAt,
            @Param("updatedBy") String updatedBy);

    /** 将发布失败记录退回等待状态并设置退避时间。 */
    int markRetry(@Param("id") long id,
            @Param("claimOwner") String claimOwner,
            @Param("nextAttemptAt") java.time.LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("updatedBy") String updatedBy);
}
