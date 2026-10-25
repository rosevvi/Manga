package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeRun;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 提供 Creative Run 的所有权、幂等和加锁查询。
 */
public interface CreativeRunMapper extends BaseMapper<CreativeRun> {
    /** 查询尚有事件未投影的 Run，支持限定会话。 */
    List<String> findUnprojected(@Param("conversationId") String conversationId, @Param("limit") int limit);
    /** 查询会话的当前活动 Run。 */
    CreativeRun findActiveConversation(@Param("conversationId") String conversationId);
    /** 查询会话内全部运行标识，供事务内重建使用。 */
    List<String> findConversationRunIds(@Param("conversationId") String conversationId);
    /** 只更新投影游标，避免覆盖并发运行状态。 */
    int updateProjectionCursor(@Param("runId") String runId, @Param("sequence") long sequence, @Param("actor") String actor);

    /** 按稳定 Run 标识查询运行快照。 */
    CreativeRun findByRunId(@Param("runId") String runId);

    /** 查询用户拥有的指定 Run。 */
    CreativeRun findOwned(@Param("runId") String runId, @Param("userId") long userId);

    /** 按用户和请求标识查询已受理 Run。 */
    CreativeRun findByRequestId(@Param("userId") long userId, @Param("requestId") String requestId);

    /** 加行锁查询用户拥有的 Run，供状态迁移使用。 */
    CreativeRun lockOwned(@Param("runId") String runId, @Param("userId") long userId);

    /** 加行锁查询 Run，供事件序号分配使用。 */
    CreativeRun lockByRunId(@Param("runId") String runId);
}
