package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.manga.agent.state.AgentStateSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 持久化 Agent 内部状态的写入围栏及保留期限。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_agent_session")
public class AgentStateSession {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** Run 独占的内部状态会话。 */
    private String stateSessionId;
    /** 所属运行。 */
    private String runId;
    /** 可信用户隔离键。 */
    private String userKey;
    /** 受理时冻结的会话代次。 */
    private Long generation;
    /** 状态写入许可，对应 AgentStateSessionStatus。 */
    private AgentStateSessionStatus status;
    /** 状态写入许可截止时间。 */
    private LocalDateTime expiresAt;
    /** 记录创建时间。 */
    private LocalDateTime createdAt;
    /** 记录更新时间。 */
    private LocalDateTime updatedAt;
    /** 记录创建操作者。 */
    private String createdBy;
    /** 记录更新操作者。 */
    private String updatedBy;
}
