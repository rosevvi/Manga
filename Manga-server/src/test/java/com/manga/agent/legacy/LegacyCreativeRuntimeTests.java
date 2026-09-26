package com.manga.agent.legacy;

import com.manga.agent.MangaAgentRuntimeService;
import com.manga.agent.api.AgentRuntimeResponseCode;
import com.manga.agent.application.StartCreativeRunCommand;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceReference;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证旧运行时不会把手动领域操作伪装成助手对话执行。
 */
class LegacyCreativeRuntimeTests {

    @Test
    void shouldRejectManualCreativeOperation() {
        MangaAgentRuntimeService runtimeService = mock(MangaAgentRuntimeService.class);
        LegacyCreativeRuntime runtime = new LegacyCreativeRuntime(runtimeService);
        StartCreativeRunCommand command = StartCreativeRunCommand.manual(101L, 202L,
                CreativeOperationType.GENERATE_CHAPTER_STORYBOARD,
                new CreativeResourceReference(CreativeResourceType.CHAPTER, 303L),
                "生成本章分镜");

        assertThatThrownBy(() -> runtime.start(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                        exception.getResponseCode()).isEqualTo(AgentRuntimeResponseCode.LEGACY_RUNTIME_UNSUPPORTED_OPERATION));
        verifyNoInteractions(runtimeService);
    }
}
