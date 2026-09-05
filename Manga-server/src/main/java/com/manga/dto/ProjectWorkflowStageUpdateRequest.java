package com.manga.dto;

import com.manga.common.enums.ProjectWorkflowStage;
import jakarta.validation.constraints.NotNull;

/** 承载项目工作流阶段的手动切换请求。 */
public record ProjectWorkflowStageUpdateRequest(
        /** 目标工作流阶段。 */
        @NotNull ProjectWorkflowStage stage
) {
}
