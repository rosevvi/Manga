package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.AgentMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 助手消息投影 Mapper。 */
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {
    List<AgentMessage> findByConversation(@Param("conversationId") String conversationId);

    Long nextOrder(@Param("conversationId") String conversationId);
}
