package com.manga.dto;

import com.manga.common.enums.ProjectStatus;
import com.manga.common.enums.ProjectVisibilityScope;
import com.manga.common.enums.ProjectWorkflowStage;

import java.time.LocalDateTime;

/** 返回项目详情及其分镜数量摘要。 */
public record ProjectResponse(
        /** 项目主键。 */
        Long id,
        /** 项目名称。 */
        String name,
        /** 项目描述。 */
        String description,
        /** 项目封面图片地址。 */
        String coverUrl,
        /** 项目作品类型。 */
        String genre,
        /** 默认画面比例。 */
        String aspectRatio,
        /** 项目可见范围。 */
        ProjectVisibilityScope visibilityScope,
        /** 项目状态。 */
        ProjectStatus status,
        /** 当前推荐创作阶段。 */
        ProjectWorkflowStage workflowStage,
        /** 画风预设标识或 custom。 */
        String artStyle,
        /** 画风显示名称。 */
        String artStyleName,
        /** 自定义画风中文描述。 */
        String artStyleDescription,
        /** 自定义画风图片生成提示词。 */
        String artStyleImagePrompt,
        /** 画风参考图片地址。 */
        String artStyleImageUrl,
        /** 项目分镜镜头数量。 */
        int shotCount,
        /** 创建时间。 */
        LocalDateTime createdAt,
        /** 最后更新时间。 */
        LocalDateTime updatedAt
) {
}
