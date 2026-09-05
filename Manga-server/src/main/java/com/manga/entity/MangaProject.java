package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.ProjectStatus;
import com.manga.common.enums.ProjectVisibilityScope;
import com.manga.common.enums.ProjectWorkflowStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 表示用户拥有的漫剧创作项目。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_project")
public class MangaProject {

    /** 项目主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 项目所有者用户主键。 */
    private Long ownerUserId;
    /** 项目名称。 */
    private String name;
    /** 项目描述。 */
    private String description;
    /** 项目封面图片地址。 */
    private String coverUrl;
    /** 项目作品类型。 */
    private String genre;
    /** 默认画面比例。 */
    private String aspectRatio;
    /** 项目可见范围。 */
    private ProjectVisibilityScope visibilityScope;
    /** 项目状态。 */
    private ProjectStatus status;
    /** 当前推荐创作阶段，来自项目工作流表。 */
    @TableField(exist = false)
    private ProjectWorkflowStage workflowStage;
    /** 画风预设标识或 custom。 */
    private String artStyle;
    /** 自定义画风中文描述。 */
    private String artStyleDescription;
    /** 自定义画风图片生成提示词。 */
    private String artStyleImagePrompt;
    /** 画风参考图片地址。 */
    private String artStyleImageUrl;
    /** 项目分镜镜头数量。 */
    @TableField(exist = false)
    private Integer shotCount;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建人标识。 */
    private String createdBy;
    /** 最后更新人标识。 */
    private String updatedBy;
}
