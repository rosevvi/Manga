package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeConversation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 提供创作会话的所有权查询和基础持久化能力。
 */
public interface CreativeConversationMapper extends BaseMapper<CreativeConversation> {
    /** 加锁查询会话，串行生成状态代次与上下文摘要。 */
    CreativeConversation lockOwned(@Param("conversationId") String conversationId, @Param("userId") long userId);
    /** 按创建主键倒序分页查询用户会话，可按项目精确过滤。 */
    List<CreativeConversation> listOwned(@Param("userId") long userId,
            @Param("projectId") Long projectId, @Param("beforeId") Long beforeId, @Param("limit") int limit);
    /** 仅当失败 Run 仍对应当前代次时递增，重复终态事件不会重复推进。 */
    int advanceGeneration(@Param("conversationId") String conversationId,
            @Param("generation") long generation, @Param("actor") String actor);

    /** 查询用户拥有的指定会话。 */
    CreativeConversation findOwned(@Param("conversationId") String conversationId,
            @Param("userId") long userId);
}
