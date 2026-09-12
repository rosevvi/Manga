package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.AgentEventOutbox;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 助手事件投递箱 Mapper。 */
public interface AgentEventOutboxMapper extends BaseMapper<AgentEventOutbox> {
    List<AgentEventOutbox> findPending(@Param("limit") int limit);
}
