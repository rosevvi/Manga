package com.manga.agent.api;

import com.manga.agent.domain.CreativeMessageRole;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeToolStatus;
import java.time.LocalDateTime;
/** 不暴露 AgentState 和模型配置的用户消息视图。 */
public record CreativeMessageView(
        /** 历史分页游标。 */ long id,
        /** 稳定逻辑消息标识，重建后仍保持一致。 */ String projectionKey,
        /** 来源 Run。 */ String runId,
        /** 用户、助手或工具角色。 */ CreativeMessageRole role,
        /** 消息所属运行状态。 */ CreativeRunStatus status,
        /** 完整可见正文。 */ String content,
        /** 可选工具名称。 */ String toolName,
        /** 可选公开工具状态。 */ CreativeToolStatus toolStatus,
        /** 消息首次产生时间。 */ LocalDateTime createdAt
) { }
