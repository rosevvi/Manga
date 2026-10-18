package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.MangaAgentState;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 提供 AgentScope 状态的版本化读写和会话级清理。
 */
public interface MangaAgentStateMapper extends BaseMapper<MangaAgentState> {

    /** 按用户、会话和组件键查询状态。 */
    MangaAgentState findOne(
            @Param("userKey") String userKey,
            @Param("stateSessionId") String stateSessionId,
            @Param("stateKey") String stateKey);

    /** 无条件写入状态；已存在时递增版本。 */
    int upsert(MangaAgentState state);

    /** 仅在目标组件不存在时创建初始版本。 */
    int insertInitial(MangaAgentState state);

    /** 使用期望版本原子更新并递增版本。 */
    int updateIfVersion(MangaAgentState state);

    /** 判断指定用户会话是否包含任意状态。 */
    int countSession(
            @Param("userKey") String userKey,
            @Param("stateSessionId") String stateSessionId);

    /** 删除指定用户会话的全部状态。 */
    int deleteSession(
            @Param("userKey") String userKey,
            @Param("stateSessionId") String stateSessionId);

    /** 删除指定用户会话的单个组件状态。 */
    int deleteState(
            @Param("userKey") String userKey,
            @Param("stateSessionId") String stateSessionId,
            @Param("stateKey") String stateKey);

    /** 列出指定用户拥有状态的会话标识。 */
    List<String> listSessionIds(@Param("userKey") String userKey);
}
