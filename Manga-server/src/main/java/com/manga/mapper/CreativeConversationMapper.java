package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeConversation;
import org.apache.ibatis.annotations.Param;

/**
 * 提供创作会话的所有权查询和基础持久化能力。
 */
public interface CreativeConversationMapper extends BaseMapper<CreativeConversation> {

    /** 查询用户拥有的指定会话。 */
    CreativeConversation findOwned(@Param("conversationId") String conversationId,
            @Param("userId") long userId);
}
