package com.manga.agent.changeset;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 审批决定；身份和资源范围只能由服务端认证上下文提供。 */
public record ApprovalRequest(
        /** 主动批准或拒绝。 */ @NotNull ApprovalDecision decision,
        /** 与决定一起持久化的评论。 */ @Size(max = 2000) String comment
) { }
