package com.manga.agent.harness;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.memory.MemoryFlushManager;
import io.agentscope.harness.agent.memory.compaction.ConversationCompactor;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 使用真实 AgentScope 压缩器验证长上下文能压缩，并且不会调用文件卸载或长期记忆管道。 */
class AgentCompactionPolicyTests {
    @Test
    void shouldCompactLongContextWithoutWorkspaceWritesAndKeepLatestRequest() {
        Model model = mock(Model.class);
        MemoryFlushManager memory = mock(MemoryFlushManager.class);
        when(model.stream(any(), isNull(), isNull())).thenReturn(Flux.just(ChatResponse.builder()
                .content(List.of(TextBlock.builder().text("角色阿青正在调查失踪事件").build())).build()));
        List<Msg> history = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            history.add(new UserMessage("历史剧情资料".repeat(200)));
        }
        Msg latestRequest = new UserMessage("保留本轮请求，继续推进故事");
        history.add(latestRequest);

        List<Msg> compacted = new ConversationCompactor(model, memory).compactIfNeeded(
                RuntimeContext.builder().userId("1").sessionId("compaction-test").build(),
                history, AgentCompactionPolicy.forContextWindow(1024), "director", "compaction-test")
                .block().orElseThrow();

        assertThat(compacted).hasSizeLessThan(history.size());
        assertThat(compacted.getFirst().getTextContent()).contains("角色阿青正在调查失踪事件");
        assertThat(compacted.getLast()).isSameAs(latestRequest);
        verifyNoInteractions(memory);
    }
}
