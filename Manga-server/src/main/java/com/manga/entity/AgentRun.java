package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.AgentRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 表示一次不可变配置下的助手执行。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_agent_run")
public class AgentRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private String conversationId;
    private Long userId;
    private Long projectId;
    private String agentKey;
    private Long providerConfigId;
    private String modelCode;
    private String kernelFingerprint;
    private String kernelSnapshotJson;
    private String stateSessionId;
    private AgentRunStatus status;
    private String activeConversationId;
    private String ownerInstanceId;
    private Long ownerEpoch;
    private LocalDateTime leaseUntil;
    private LocalDateTime deadlineAt;
    private Long nextSequence;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
