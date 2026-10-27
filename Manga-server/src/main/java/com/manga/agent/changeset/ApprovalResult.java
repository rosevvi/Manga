package com.manga.agent.changeset;

import java.util.List;

/** 持久化恢复命令；Agent 只能解释结果，不能据此自行批准其他提案。 */
public record ApprovalResult(
        /** 已处理提案的稳定标识。 */ String changeSetId,
        /** 审批最终结果，包含拒绝、过期和冲突。 */ ApprovalStatus status,
        /** 审批人的评论或系统结束原因。 */ String comment,
        /** 实际创建或更新的业务主键。 */ List<Long> resourceIds
) { }
