package com.manga.agent.application;

import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeRunTriggerSource;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeRunRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 Creative Run 查询保持状态和所有权边界。
 */
class CreativeRunQueryServiceTests {

    private static final String RUN_ID = "run-test";
    private static final long USER_ID = 101L;

    @Test
    void shouldReturnPersistedCreativeStatus() {
        CreativeRunRepository repository = mock(CreativeRunRepository.class);
        CreativeRunQueryService service = new CreativeRunQueryService(repository);
        CreativeRun run = CreativeRun.builder()
                .runId(RUN_ID)
                .requestId("request-test")
                .userId(USER_ID)
                .projectId(202L)
                .triggerSource(CreativeRunTriggerSource.CONVERSATION)
                .operationType(CreativeOperationType.ASSISTANT_CHAT)
                .permissionPolicy(AgentPermissionPolicy.READ_ONLY)
                .engineVersion("durable-v1")
                .workflowVersion("v1")
                .status(CreativeRunStatus.QUEUED)
                .build();
        when(repository.findOwned(RUN_ID, USER_ID)).thenReturn(Optional.of(run));

        assertThat(service.findOwned(RUN_ID, USER_ID).status()).isEqualTo(CreativeRunStatus.QUEUED);
    }

    @Test
    void shouldRejectUnknownOrUnownedRun() {
        CreativeRunRepository repository = mock(CreativeRunRepository.class);
        CreativeRunQueryService service = new CreativeRunQueryService(repository);
        when(repository.findOwned(RUN_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOwned(RUN_ID, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                        exception.getResponseCode()).isEqualTo(CommonResponseCode.FORBIDDEN));
    }
}
