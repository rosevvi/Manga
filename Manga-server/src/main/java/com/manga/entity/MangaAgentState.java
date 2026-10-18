package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.state.AgentStateKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 表示一个可版本化恢复的 AgentScope 组件状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_agent_state")
public class MangaAgentState {

    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** AgentScope 用户隔离键。 */
    private String userKey;
    /** AgentScope 状态会话标识。 */
    private String stateSessionId;
    /** 会话内组件状态键。 */
    private String stateKey;
    /** 单对象或列表载荷类型。 */
    private AgentStateKind stateKind;
    /** 序列化前的 State Java 类型名称。 */
    private String stateType;
    /** AgentScope State JSON 载荷。 */
    private String stateJson;
    /** 乐观并发控制版本号。 */
    private Long stateVersion;
    /** 记录创建时间。 */
    private LocalDateTime createdAt;
    /** 记录最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 记录创建操作者。 */
    private String createdBy;
    /** 记录最后更新操作者。 */
    private String updatedBy;
}
