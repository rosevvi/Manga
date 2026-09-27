package com.manga.agent.domain;

import com.manga.agent.api.CreativeRuntimeResponseCode;
import com.manga.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/**
 * 校验 Creative Run 的合法状态迁移并保护终态不可逆约束。
 */
public final class CreativeRunStateMachine {

    /** 每个非终态允许进入的后继状态。 */
    private static final Map<CreativeRunStatus, EnumSet<CreativeRunStatus>> TRANSITIONS = transitions();

    /** 禁止实例化无状态状态机。 */
    private CreativeRunStateMachine() {
    }

    /** 判断一次状态迁移是否符合 Creative Run 契约。 */
    public static boolean canTransition(CreativeRunStatus current, CreativeRunStatus target) {
        EnumSet<CreativeRunStatus> allowed = TRANSITIONS.get(current);
        return Objects.nonNull(allowed) && allowed.contains(target);
    }

    /** 校验状态迁移，非法时返回稳定业务错误码。 */
    public static void requireTransition(CreativeRunStatus current, CreativeRunStatus target) {
        if (!canTransition(current, target)) {
            throw new BusinessException(
                    CreativeRuntimeResponseCode.INVALID_RUN_STATE_TRANSITION,
                    CreativeRuntimeResponseCode.INVALID_RUN_STATE_TRANSITION.message()
                            .formatted(current.code(), target.code()),
                    HttpStatus.CONFLICT
            );
        }
    }

    /** 构建不可变的 Run 状态迁移表。 */
    private static Map<CreativeRunStatus, EnumSet<CreativeRunStatus>> transitions() {
        EnumMap<CreativeRunStatus, EnumSet<CreativeRunStatus>> transitions =
                new EnumMap<>(CreativeRunStatus.class);
        transitions.put(CreativeRunStatus.QUEUED, EnumSet.of(
                CreativeRunStatus.ACTIVE,
                CreativeRunStatus.CANCEL_REQUESTED,
                CreativeRunStatus.FAILED
        ));
        transitions.put(CreativeRunStatus.ACTIVE, EnumSet.of(
                CreativeRunStatus.WAITING,
                CreativeRunStatus.COMPLETED,
                CreativeRunStatus.CANCEL_REQUESTED,
                CreativeRunStatus.FAILED
        ));
        transitions.put(CreativeRunStatus.WAITING, EnumSet.of(
                CreativeRunStatus.ACTIVE,
                CreativeRunStatus.COMPLETED,
                CreativeRunStatus.CANCEL_REQUESTED,
                CreativeRunStatus.FAILED
        ));
        transitions.put(CreativeRunStatus.CANCEL_REQUESTED, EnumSet.of(
                CreativeRunStatus.CANCELLED,
                CreativeRunStatus.FAILED
        ));
        return Map.copyOf(transitions);
    }
}
