package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeRun;
import org.apache.ibatis.annotations.Param;

/**
 * 提供 Creative Run 的所有权、幂等和加锁查询。
 */
public interface CreativeRunMapper extends BaseMapper<CreativeRun> {

    /** 查询用户拥有的指定 Run。 */
    CreativeRun findOwned(@Param("runId") String runId, @Param("userId") long userId);

    /** 按用户和请求标识查询已受理 Run。 */
    CreativeRun findByRequestId(@Param("userId") long userId, @Param("requestId") String requestId);

    /** 加行锁查询用户拥有的 Run，供状态迁移使用。 */
    CreativeRun lockOwned(@Param("runId") String runId, @Param("userId") long userId);

    /** 加行锁查询 Run，供事件序号分配使用。 */
    CreativeRun lockByRunId(@Param("runId") String runId);
}
