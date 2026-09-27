package com.manga.repository;

import com.manga.entity.CreativeConversation;
import com.manga.mapper.CreativeConversationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 封装创作会话的持久化访问。
 */
@Repository
@RequiredArgsConstructor
public class CreativeConversationRepository {

    private final CreativeConversationMapper mapper;

    /** 查询用户拥有的指定会话。 */
    public Optional<CreativeConversation> findOwned(String conversationId, long userId) {
        return Optional.ofNullable(mapper.findOwned(conversationId, userId));
    }

    /** 新增创作会话并回填数据库主键。 */
    public CreativeConversation create(CreativeConversation conversation) {
        mapper.insert(conversation);
        return conversation;
    }

    /** 更新会话绑定项目和审计信息。 */
    public void update(CreativeConversation conversation) {
        mapper.updateById(conversation);
    }
}
