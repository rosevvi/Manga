package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.agent.changeset.ChangeSetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 冻结 AI 提案、基础版本和业务应用结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_creative_change_set")
public class CreativeChangeSet {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 对外稳定标识。 */
    private String changeSetId;
    /** 提案所属 Run。 */
    private String runId;
    /** 提案所属 Step；一个步骤只生成一个不可变提案。 */
    private String stepId;
    /** 发起用户主键。 */
    private Long userId;
    /** 可信运行上下文绑定的项目。 */
    private Long projectId;
    /** 可选目标章节，创建章节时为空。 */
    private Long chapterId;
    /** 业务 Schema 类型。 */
    private ChangeSetKind kind;
    /** 不可变提案 Schema 版本。 */
    private Integer schemaVersion;
    /** 原始用户指令，重新生成时保留任务约束。 */
    private String instruction;
    /** 当前聚合状态。 */
    private ChangeSetStatus status;
    /** 开始推理时冻结的项目版本。 */
    private Long baseProjectRevision;
    /** 提案目标章节的基础版本。 */
    private Long baseChapterRevision;
    /** 用户预览使用的原始业务内容。 */
    private String beforeJson;
    /** 通过校验且不可修改的提案正文。 */
    private String payloadJson;
    /** 应用或拒绝后持久化的结构化恢复结果。 */
    private String resultJson;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 更新操作者。 */
    private String updatedBy;
}

