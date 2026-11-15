package com.manga.agent.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.capability.CapabilityContractSupport;
import com.manga.agent.capability.CapabilityName;
import com.manga.agent.capability.CapabilityResult;
import com.manga.agent.capability.CapabilitySummary;
import com.manga.agent.capability.MangaAgentCapability;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.event.ToolActivitySummaries;
import com.manga.entity.MangaProject;
import com.manga.service.ProjectRevisionService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 通过真实 Tool 适配器验证版本缓存、执行隔离和模型可读的校验错误。 */
class MangaCapabilityToolTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentRuntimeContext context = new AgentRuntimeContext(1L, 1L, null, "run", "step", "session", "owner", 1L,
            Instant.now().plusSeconds(60), AgentPermissionPolicy.READ_ONLY);

    /** 非领域故障只返回安全错误，并阻止下一次推理及 Compaction 再次计费。 */
    @Test
    void unexpectedFailureCannotLeakSqlOrTriggerAnotherPaidTurn() {
        var revisions = mock(ProjectRevisionService.class);
        var project = new MangaProject(); project.setRevision(1L);
        when(revisions.lock(anyLong(), anyLong())).thenReturn(project);
        var capability = capability();
        when(capability.execute(any(), any())).thenThrow(new IllegalStateException("private-sql-and-connection-details"));
        var activity = new InvocationActivity(3);
        var summaries = new ToolActivitySummaries();
        var tool = new MangaCapabilityTool(capability, mapper, summaries, new ToolExecutionSupport(mapper, revisions), activity);
        assertThat(tool.callAsync(param("unavailable")).block().getState()).isEqualTo(ToolResultState.ERROR);
        assertThat(summaries.take("run", "step", 1L, "unavailable").failureReason()).doesNotContain("private-sql");
        assertThatThrownBy(() -> activity.beforeModel(false)).isInstanceOf(AgentRuntimeException.class)
                .hasMessageNotContaining("private-sql");
    }

    @Test
    void cacheIsRevisionBoundAndReportsReusedCalls() {
        var revisions = mock(ProjectRevisionService.class);
        var project = new MangaProject(); project.setRevision(1L);
        when(revisions.lock(anyLong(), anyLong())).thenReturn(project);
        var support = new ToolExecutionSupport(mapper, revisions);
        var summaries = new ToolActivitySummaries();
        var capability = capability();
        when(capability.execute(any(), any())).thenReturn(new CapabilityResult(mapper.createObjectNode().put("content", "正文"), CapabilitySummary.chapter(1, "初入")));
        var tool = new MangaCapabilityTool(capability, mapper, summaries, support, new InvocationActivity(3));
        tool.callAsync(param("first")).block(); tool.callAsync(param("second")).block();
        verify(capability, times(1)).execute(any(), any());
        var reused = summaries.take("run", "step", 1L, "second");
        assertThat(reused.reused()).isTrue(); assertThat(reused.callCount()).isEqualTo(2);
        assertThat(reused.actionLabel()).contains("初入");
        project.setRevision(2L); tool.callAsync(param("third")).block();
        verify(capability, times(2)).execute(any(), any());
        new MangaCapabilityTool(capability, mapper, summaries, support, new InvocationActivity(3)).callAsync(param("fresh-harness")).block();
        verify(capability, times(3)).execute(any(), any());
    }

    @Test
    void schemaFailureReturnsErrorStateAndSafeFieldDetails() {
        var revisions = mock(ProjectRevisionService.class);
        var project = new MangaProject(); project.setRevision(1L);
        when(revisions.lock(anyLong(), anyLong())).thenReturn(project);
        var capability = capability();
        when(capability.execute(any(), any())).thenThrow(new ToolInputValidationException(
                List.of(new ToolInputValidationException.FieldIssue("content", "长度不能超过 2000"))));
        var summaries = new ToolActivitySummaries();
        var tool = new MangaCapabilityTool(capability, mapper, summaries, new ToolExecutionSupport(mapper, revisions), new InvocationActivity(3));
        assertThat(tool.callAsync(param("invalid")).block().getState()).isEqualTo(ToolResultState.ERROR);
        assertThat(summaries.take("run", "step", 1L, "invalid").failureReason()).contains("content", "2000");
    }

    /** 使用真实白名单描述，模型返回由测试替身控制。 */
    private MangaAgentCapability capability() {
        var capability = mock(MangaAgentCapability.class);
        when(capability.descriptor()).thenReturn(CapabilityContractSupport.readOnlyDescriptor(mapper,
                CapabilityName.GET_CHAPTER_CONTENT, "读取章节", "{\"type\":\"object\"}", 1));
        return capability;
    }
    /** 构造同一可信执行上下文中不同调用标识的参数。 */
    private ToolCallParam param(String id) {
        var param = mock(ToolCallParam.class); var use = mock(ToolUseBlock.class);
        when(use.getId()).thenReturn(id); when(param.getToolUseBlock()).thenReturn(use);
        when(param.getInput()).thenReturn(Map.of("chapterId", 1));
        when(param.getRuntimeContext()).thenReturn(RuntimeContext.builder().put(AgentRuntimeContext.class, context).build());
        return param;
    }
}
