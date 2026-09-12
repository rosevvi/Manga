package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.AgentConversation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 助手会话 Mapper。 */
public interface AgentConversationMapper extends BaseMapper<AgentConversation> {
    AgentConversation findOwned(@Param("conversationId") String conversationId, @Param("userId") long userId);

    List<AgentConversation> findByUser(@Param("userId") long userId, @Param("projectId") Long projectId);
}
