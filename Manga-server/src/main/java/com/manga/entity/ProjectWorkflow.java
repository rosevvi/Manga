package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.ProjectWorkflowStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 保存项目当前推荐创作阶段。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_project_workflow")
public class ProjectWorkflow {

    /** 工作流记录主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属项目主键。 */
    private Long projectId;
    /** 当前推荐阶段。 */
    private ProjectWorkflowStage currentStage;
    /** 阶段记录版本号。 */
    private Integer stageRevision;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建人标识。 */
    private String createdBy;
    /** 最后更新人标识。 */
    private String updatedBy;
}
