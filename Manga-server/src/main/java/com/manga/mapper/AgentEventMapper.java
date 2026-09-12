package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.AgentEvent;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 助手事件日志 Mapper。 */
public interface AgentEventMapper extends BaseMapper<AgentEvent> {
    List<AgentEvent> findAfter(@Param("runId") String runId, @Param("afterSequence") long afterSequence);
}
