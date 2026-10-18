package com.manga.repository;

import com.manga.entity.MangaAgentState;
import com.manga.mapper.MangaAgentStateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 封装 AgentScope 状态行的版本化持久化操作。
 */
@Repository
@RequiredArgsConstructor
public class MangaAgentStateRepository {

    private final MangaAgentStateMapper mapper;

    /** 查询唯一组件状态。 */
    public Optional<MangaAgentState> find(String userKey, String stateSessionId, String stateKey) {
        return Optional.ofNullable(mapper.findOne(userKey, stateSessionId, stateKey));
    }

    /** 无条件保存并递增版本。 */
    public void upsert(MangaAgentState state) {
        mapper.upsert(state);
    }

    /** 创建版本为一的初始状态。 */
    public boolean insertInitial(MangaAgentState state) {
        return mapper.insertInitial(state) == 1;
    }

    /** 按期望版本更新状态。 */
    public boolean updateIfVersion(MangaAgentState state) {
        return mapper.updateIfVersion(state) == 1;
    }

    /** 判断会话是否存在状态。 */
    public boolean exists(String userKey, String stateSessionId) {
        return mapper.countSession(userKey, stateSessionId) > 0;
    }

    /** 删除会话全部状态。 */
    public void deleteSession(String userKey, String stateSessionId) {
        mapper.deleteSession(userKey, stateSessionId);
    }

    /** 删除会话内单个组件状态。 */
    public void deleteState(String userKey, String stateSessionId, String stateKey) {
        mapper.deleteState(userKey, stateSessionId, stateKey);
    }

    /** 列出用户拥有状态的全部会话标识。 */
    public List<String> listSessionIds(String userKey) {
        return mapper.listSessionIds(userKey);
    }
}
