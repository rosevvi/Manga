package com.manga.agent.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定持久化和 API 使用的枚举机器编码，防止重构时静默破坏历史数据。
 */
class CreativeContractEnumTests {

    @Test
    void shouldKeepRunStatusCodesStable() {
        assertThat(codes(CreativeRunStatus.values())).containsExactly(
                "QUEUED", "ACTIVE", "WAITING", "CANCEL_REQUESTED", "COMPLETED", "FAILED", "CANCELLED");
        for (CreativeRunStatus status : CreativeRunStatus.values()) {
            assertThat(CreativeRunStatus.fromCode(status.code())).isSameAs(status);
        }
    }

    @Test
    void shouldKeepStepStatusCodesStable() {
        assertThat(codes(CreativeStepStatus.values())).containsExactly(
                "PENDING", "READY", "RUNNING", "WAITING_APPROVAL", "WAITING_JOB", "WAITING_CHILDREN",
                "WAITING_RETRY", "COMPLETED", "FAILED", "CANCEL_REQUESTED", "CANCELLED", "SKIPPED");
        for (CreativeStepStatus status : CreativeStepStatus.values()) {
            assertThat(CreativeStepStatus.fromCode(status.code())).isSameAs(status);
        }
    }

    @Test
    void shouldKeepPermissionAndErrorCodesStable() {
        assertThat(codes(AgentPermissionPolicy.values())).containsExactly(
                "READ_ONLY", "PROPOSE_CHANGES", "CONFIRM_SIDE_EFFECTS", "AUTOMATION");
        assertThat(codes(CreativeErrorCode.values())).containsExactly(
                "INVALID_REQUEST", "PERMISSION_DENIED", "DUPLICATE_REQUEST", "ACTIVE_RUN_CONFLICT",
                "RUNTIME_UNAVAILABLE", "MODEL_INVOCATION_FAILED", "CAPABILITY_EXECUTION_FAILED",
                "OWNER_LEASE_LOST", "DEADLINE_EXCEEDED", "INVALID_STATE_TRANSITION",
                "CANCELLED_BY_USER", "INTERNAL_ERROR");
    }

    /** 提取实现 code 方法的契约枚举机器编码。 */
    private String[] codes(CreativeRunStatus[] values) {
        return Arrays.stream(values).map(CreativeRunStatus::code).toArray(String[]::new);
    }

    /** 提取 Step 状态机器编码。 */
    private String[] codes(CreativeStepStatus[] values) {
        return Arrays.stream(values).map(CreativeStepStatus::code).toArray(String[]::new);
    }

    /** 提取权限策略机器编码。 */
    private String[] codes(AgentPermissionPolicy[] values) {
        return Arrays.stream(values).map(AgentPermissionPolicy::code).toArray(String[]::new);
    }

    /** 提取错误类型机器编码。 */
    private String[] codes(CreativeErrorCode[] values) {
        return Arrays.stream(values).map(CreativeErrorCode::code).toArray(String[]::new);
    }
}
