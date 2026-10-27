package com.manga.agent.changeset;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/** 审批预览与历史审计的公开投影，不暴露内部模型状态或 Blueprint。 */
public record ChangeSetView(
        /** 分页使用的数据库主键。 */ Long id,
        /** 对外稳定提案标识。 */ String changeSetId,
        /** 来源运行标识。 */ String runId,
        /** 来源步骤标识。 */ String stepId,
        /** 绑定项目。 */ Long projectId,
        /** 可选目标章节。 */ Long chapterId,
        /** 提案业务类型。 */ ChangeSetKind kind,
        /** 提案 Schema 版本。 */ int schemaVersion,
        /** 产生提案的原始用户任务，用于重新生成。 */ String instruction,
        /** 提案当前状态。 */ ChangeSetStatus status,
        /** 生成前冻结的项目版本。 */ long baseProjectRevision,
        /** 可选章节版本。 */ Long baseChapterRevision,
        /** 业务原文快照。 */ JsonNode before,
        /** 已冻结的待应用内容。 */ JsonNode proposed,
        /** 可选审批状态；草稿尚未开放审批。 */ ApprovalStatus approvalStatus,
        /** 可选审批截止时间。 */ LocalDateTime expiresAt,
        /** 真实审批人；系统终止时为空。 */ Long decidedBy,
        /** 可选最终决策时间。 */ LocalDateTime decidedAt,
        /** 审批评论。 */ String comment
) { }
