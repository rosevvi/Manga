package com.manga.agent.changeset;

import com.manga.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Objects;

/** 显式保护提案终态，拒绝重新批准、改写或复用已处理提案。 */
public final class ChangeSetStateMachine {
    private ChangeSetStateMachine() { }

    /** 只允许草稿开放审批，以及待审批进入一个终态。 */
    public static void requireTransition(ChangeSetStatus from, ChangeSetStatus to) {
        boolean allowed = Objects.nonNull(from) && Objects.nonNull(to) && (from == ChangeSetStatus.DRAFT
                ? to == ChangeSetStatus.PENDING || to == ChangeSetStatus.CANCELLED
                : from == ChangeSetStatus.PENDING && to != ChangeSetStatus.DRAFT && to != ChangeSetStatus.PENDING);
        if (!allowed) throw new BusinessException(ChangeSetResponseCode.STATE_CONFLICT, HttpStatus.CONFLICT);
    }
}
