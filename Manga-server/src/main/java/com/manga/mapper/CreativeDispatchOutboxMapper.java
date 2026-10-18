package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeDispatchOutbox;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提供 Dispatch Outbox 的竞争领取和状态推进。
 */
public interface CreativeDispatchOutboxMapper extends BaseMapper<CreativeDispatchOutbox> {

    /** 查询到期或领取超时的候选记录。 */
    List<CreativeDispatchOutbox> findClaimCandidates(@Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /** 原子领取一条候选记录。 */
    int claim(@Param("id") long id,
            @Param("claimOwner") String claimOwner,
            @Param("claimUntil") LocalDateTime claimUntil,
            @Param("now") LocalDateTime now,
            @Param("updatedBy") String updatedBy);

    /** 将当前实例领取的记录标记为已投递。 */
    int markDispatched(@Param("id") long id,
            @Param("claimOwner") String claimOwner,
            @Param("dispatchedAt") LocalDateTime dispatchedAt,
            @Param("updatedBy") String updatedBy);

    /** 将投递失败记录退回等待状态并设置退避时间。 */
    int markRetry(@Param("id") long id,
            @Param("claimOwner") String claimOwner,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("updatedBy") String updatedBy);
}
