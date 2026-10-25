package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.AgentStateSession;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
/** 以数据库行锁保护 StateStore 写入许可。 */
public interface AgentStateSessionMapper extends BaseMapper<AgentStateSession> {
    /** 加锁读取指定用户的状态会话；写入和撤销使用相同的锁。 */
    AgentStateSession lockSession(@Param("userKey") String userKey, @Param("sessionId") String sessionId);
    /** 撤销一个 Run 下所有仍开放的状态会话。 */
    int retireRun(@Param("runId") String runId, @Param("actor") String actor);
    /** 查询超过到期时间及保留窗口的会话。 */
    List<AgentStateSession> expired(@Param("before") LocalDateTime before, @Param("limit") int limit);
    /** 删除已锁定的过期会话登记。 */
    int deleteSession(@Param("sessionId") String sessionId);
}
