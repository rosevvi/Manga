package com.manga.dto;

import com.manga.common.enums.ProjectWorkflowStage;

/** 返回项目详情工作区及各创作阶段摘要。 */
public record ProjectWorkspaceResponse(
        /** 项目基础信息。 */
        ProjectResponse project,
        /** 当前推荐工作流阶段。 */
        ProjectWorkflowStage workflowStage,
        /** 当前阶段版本。 */
        int stageRevision,
        /** 当前项目剧本。 */
        ProjectScriptResponse script,
        /** 剧本分集数量。 */
        int scriptEpisodeCount,
        /** 剧本场景数量。 */
        int scriptSceneCount,
        /** 项目分镜数量。 */
        int storyboardShotCount
) {
}
