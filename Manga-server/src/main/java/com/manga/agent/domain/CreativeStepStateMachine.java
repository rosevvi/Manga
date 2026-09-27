package com.manga.agent.domain;

import com.manga.agent.api.CreativeRuntimeResponseCode;
import com.manga.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/**
 * 校验 Creative Step 的合法状态迁移并保护终态不可逆约束。
 */
public final class CreativeStepStateMachine {

    /** 每个非终态允许进入的后继状态。 */
    private static final Map<CreativeStepStatus, EnumSet<CreativeStepStatus>> TRANSITIONS = transitions();

    /** 禁止实例化无状态状态机。 */
    private CreativeStepStateMachine() {
    }

    /** 判断一次状态迁移是否符合 Creative Step 契约。 */
    public static boolean canTransition(CreativeStepStatus current, CreativeStepStatus target) {
        EnumSet<CreativeStepStatus> allowed = TRANSITIONS.get(current);
        return Objects.nonNull(allowed) && allowed.contains(target);
    }

    /** 校验状态迁移，非法时返回稳定业务错误码。 */
    public static void requireTransition(CreativeStepStatus current, CreativeStepStatus target) {
        if (!canTransition(current, target)) {
            throw new BusinessException(
                    CreativeRuntimeResponseCode.INVALID_STEP_STATE_TRANSITION,
                    CreativeRuntimeResponseCode.INVALID_STEP_STATE_TRANSITION.message()
                            .formatted(current.code(), target.code()),
                    HttpStatus.CONFLICT
            );
        }
    }

    /** 构建不可变的 Step 状态迁移表。 */
    private static Map<CreativeStepStatus, EnumSet<CreativeStepStatus>> transitions() {
        EnumMap<CreativeStepStatus, EnumSet<CreativeStepStatus>> transitions =
                new EnumMap<>(CreativeStepStatus.class);
        transitions.put(CreativeStepStatus.PENDING, EnumSet.of(
                CreativeStepStatus.READY,
                CreativeStepStatus.CANCEL_REQUESTED,
                CreativeStepStatus.SKIPPED
        ));
        transitions.put(CreativeStepStatus.READY, EnumSet.of(
                CreativeStepStatus.RUNNING,
                CreativeStepStatus.CANCEL_REQUESTED,
                CreativeStepStatus.SKIPPED
        ));
        transitions.put(CreativeStepStatus.RUNNING, EnumSet.of(
                CreativeStepStatus.WAITING_APPROVAL,
                CreativeStepStatus.WAITING_JOB,
                CreativeStepStatus.WAITING_CHILDREN,
                CreativeStepStatus.WAITING_RETRY,
                CreativeStepStatus.COMPLETED,
                CreativeStepStatus.FAILED,
                CreativeStepStatus.CANCEL_REQUESTED
        ));
        transitions.put(CreativeStepStatus.WAITING_APPROVAL, resumeTransitions());
        transitions.put(CreativeStepStatus.WAITING_JOB, resumeTransitions());
        transitions.put(CreativeStepStatus.WAITING_CHILDREN, resumeTransitions());
        transitions.put(CreativeStepStatus.WAITING_RETRY, EnumSet.of(
                CreativeStepStatus.READY,
                CreativeStepStatus.FAILED,
                CreativeStepStatus.CANCEL_REQUESTED
        ));
        transitions.put(CreativeStepStatus.CANCEL_REQUESTED, EnumSet.of(
                CreativeStepStatus.CANCELLED,
                CreativeStepStatus.FAILED
        ));
        return Map.copyOf(transitions);
    }

    /** 返回等待类状态共有的恢复和终止迁移。 */
    private static EnumSet<CreativeStepStatus> resumeTransitions() {
        return EnumSet.of(
                CreativeStepStatus.READY,
                CreativeStepStatus.COMPLETED,
                CreativeStepStatus.FAILED,
                CreativeStepStatus.CANCEL_REQUESTED
        );
    }
}
