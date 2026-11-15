package com.manga.agent.api;

import com.manga.agent.collaboration.RepairAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 用户只确认下一轮修订或结束，不在此接口批准候选写入。 */
public record RepairDecisionRequest(
        /** 客户端生成的幂等命令标识。 */ @NotBlank @Size(max = 64) String requestId,
        /** 修订或结束命令。 */ @NotNull RepairAction action,
        /** 可选补充意见。 */ @Size(max = 2000) String comment
) { }
