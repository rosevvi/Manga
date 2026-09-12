package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.AgentRun;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 助手运行 Mapper。 */
public interface AgentRunMapper extends BaseMapper<AgentRun> {
    AgentRun findOwned(@Param("runId") String runId, @Param("userId") long userId);

    AgentRun lockByRunId(@Param("runId") String runId);

    List<AgentRun> findExpiredRunning(@Param("now") LocalDateTime now);
}
