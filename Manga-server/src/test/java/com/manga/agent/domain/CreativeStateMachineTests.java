package com.manga.agent.domain;

import com.manga.agent.api.CreativeRuntimeResponseCode;
import com.manga.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Creative Run 和 Step 状态机的合法迁移与终态不可逆约束。
 */
class CreativeStateMachineTests {

    @Test
    void shouldAllowRunToWaitResumeAndComplete() {
        assertThat(CreativeRunStateMachine.canTransition(
                CreativeRunStatus.QUEUED, CreativeRunStatus.ACTIVE)).isTrue();
        assertThat(CreativeRunStateMachine.canTransition(
                CreativeRunStatus.ACTIVE, CreativeRunStatus.WAITING)).isTrue();
        assertThat(CreativeRunStateMachine.canTransition(
                CreativeRunStatus.WAITING, CreativeRunStatus.ACTIVE)).isTrue();
        assertThat(CreativeRunStateMachine.canTransition(
                CreativeRunStatus.WAITING, CreativeRunStatus.COMPLETED)).isTrue();
    }

    @Test
    void shouldRejectRunTransitionAfterTerminalState() {
        assertThatThrownBy(() -> CreativeRunStateMachine.requireTransition(
                CreativeRunStatus.COMPLETED, CreativeRunStatus.ACTIVE))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                        exception.getResponseCode()).isEqualTo(
                        CreativeRuntimeResponseCode.INVALID_RUN_STATE_TRANSITION));
    }

    @Test
    void shouldAllowStepToWaitForExternalJobAndResume() {
        assertThat(CreativeStepStateMachine.canTransition(
                CreativeStepStatus.RUNNING, CreativeStepStatus.WAITING_JOB)).isTrue();
        assertThat(CreativeStepStateMachine.canTransition(
                CreativeStepStatus.WAITING_JOB, CreativeStepStatus.READY)).isTrue();
        assertThat(CreativeStepStateMachine.canTransition(
                CreativeStepStatus.READY, CreativeStepStatus.RUNNING)).isTrue();
        assertThat(CreativeStepStateMachine.canTransition(
                CreativeStepStatus.RUNNING, CreativeStepStatus.COMPLETED)).isTrue();
    }

    @Test
    void shouldRejectStepTransitionAfterTerminalState() {
        assertThatThrownBy(() -> CreativeStepStateMachine.requireTransition(
                CreativeStepStatus.CANCELLED, CreativeStepStatus.READY))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                        exception.getResponseCode()).isEqualTo(
                        CreativeRuntimeResponseCode.INVALID_STEP_STATE_TRANSITION));
    }
}
