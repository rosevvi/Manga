package com.manga.agent.changeset;

import com.manga.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Objects;

/** 审批只能从未决状态进入一个最终结果，重复命令由应用服务幂等返回。 */
public final class ApprovalStateMachine {
    private ApprovalStateMachine() { }
    /** 校验首次决定，禁止覆盖已作出的审批事实。 */
    public static void requireDecision(ApprovalStatus current, ApprovalStatus target) {
        if (current != ApprovalStatus.PENDING || Objects.isNull(target) || target == ApprovalStatus.PENDING) {
            throw new BusinessException(ChangeSetResponseCode.STATE_CONFLICT, HttpStatus.CONFLICT);
        }
    }
}
