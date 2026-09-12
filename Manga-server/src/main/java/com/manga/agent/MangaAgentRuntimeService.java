package com.manga.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.config.properties.MangaAgentProperties;
import com.manga.dto.AgentConversationResponse;
import com.manga.dto.AgentMessageResponse;
import com.manga.dto.AgentRunCreateRequest;
import com.manga.dto.AgentRunResponse;
import com.manga.dto.ResolvedAiProviderConfig;
import com.manga.entity.AgentConversation;
import com.manga.entity.AgentMessage;
import com.manga.entity.AgentRun;
import com.manga.repository.AgentConversationRepository;
import com.manga.repository.AgentMessageRepository;
import com.manga.repository.AgentRunRepository;
import com.manga.service.AiProviderConfigService;
import com.manga.service.ProjectService;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.Disposable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/** 创建、执行、取消和回收首期只读助手运行。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MangaAgentRuntimeService {

    private static final String AGENT_KEY = "manga_director";
    private static final String SYSTEM_PROMPT = """
            你是 Manga 的创作助手。请使用中文，依据用户明确绑定的项目上下文分析剧本与分镜，给出可执行的创作建议。
            你只能读取项目资料，不能声称已修改、保存、删除或生成任何业务数据。
            需要项目资料时调用 get_project_context；没有绑定项目时先提示用户绑定项目，不得猜测项目内容。
            回答保持简洁、具体，并明确区分事实与建议。
            """;
    private static final String INSTANCE_ID = "manga-agent-" + UUID.randomUUID();

    private final AgentConversationRepository conversationRepository;
    private final AgentRunRepository runRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentEventService eventService;
    private final AiProviderConfigService providerConfigService;
    private final MangaAgentModelFactory modelFactory;
    private final MangaAgentHarness harness;
    private final ProjectService projectService;
    private final MangaAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    @Qualifier("applicationTaskExecutor")
    private final Executor executor;

    private final ConcurrentMap<String, Disposable> activeExecutions = new ConcurrentHashMap<>();

    public AgentRunResponse start(long userId, AgentRunCreateRequest request) {
        StartedRun started = transactionTemplate.execute(status -> createRun(userId, request));
        if (started == null) {
            throw new IllegalStateException("助手运行创建事务未返回结果");
        }
        executor.execute(() -> execute(started.run().getRunId(), started.message()));
        return toResponse(started.run());
    }

    private StartedRun createRun(long userId, AgentRunCreateRequest request) {
        Long requestedProjectId = request.projectId();
        if (requestedProjectId != null) {
            projectService.requireAccessibleProject(requestedProjectId, userId);
        }
        AgentConversation conversation = resolveConversation(userId, request.conversationId(), requestedProjectId, request.message());
        Long projectId = requestedProjectId == null ? conversation.getProjectId() : requestedProjectId;
        if (projectId != null) {
            projectService.requireAccessibleProject(projectId, userId);
        }
        ResolvedAiProviderConfig providerConfig = providerConfigService.resolveDefault(userId)
                .orElseThrow(() -> new BusinessException(CommonResponseCode.VALIDATION_ERROR,
                        "请先在 AI 配置中保存并启用默认文本模型", HttpStatus.UNPROCESSABLE_ENTITY));
        if (!modelFactory.isOpenAiCompatible(providerConfig.providerType())) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR,
                    "当前默认 AI 配置不是 OpenAI 兼容文本服务", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (providerConfig.defaultModel() == null || providerConfig.defaultModel().isBlank()) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR,
                    "当前默认 AI 配置缺少默认模型", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String runId = UUID.randomUUID().toString();
        String stateSessionId = "manga:agent:" + conversation.getConversationId() + ':' + AGENT_KEY;
        MangaKernelSpec kernel = buildKernel(providerConfig);
        LocalDateTime now = LocalDateTime.now();
        AgentRun run = AgentRun.builder()
                .runId(runId)
                .conversationId(conversation.getConversationId())
                .userId(userId)
                .projectId(projectId)
                .agentKey(AGENT_KEY)
                .providerConfigId(providerConfig.id())
                .modelCode(providerConfig.defaultModel())
                .kernelFingerprint(kernel.fingerprint())
                .kernelSnapshotJson(write(kernel))
                .stateSessionId(stateSessionId)
                .status(AgentRunStatus.RUNNING)
                .activeConversationId(conversation.getConversationId())
                .ownerInstanceId(INSTANCE_ID)
                .ownerEpoch(1L)
                .leaseUntil(now.plus(properties.getRuntime().getOwnerLease()))
                .deadlineAt(now.plus(properties.getRuntime().getRunTimeout()))
                .nextSequence(1L)
                .startedAt(now)
                .build();
        try {
            runRepository.create(run);
        } catch (RuntimeException exception) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR, "该会话已有运行中的助手任务", HttpStatus.CONFLICT);
        }
        saveMessage(conversation.getConversationId(), runId, "USER", request.message().trim());
        eventService.append(runId, AgentEventType.RUN_STARTED, Map.of("conversationId", conversation.getConversationId()));
        return new StartedRun(run, request.message().trim());
    }

    private void execute(String runId, String userMessage) {
        AgentRun run = requireRun(runId);
        try {
            ResolvedAiProviderConfig providerConfig = providerConfigService.resolveOwned(run.getUserId(), run.getProviderConfigId())
                    .orElseThrow(() -> new IllegalStateException("运行使用的 AI 配置已不可用"));
            MangaKernelSpec kernel = readKernel(run.getKernelSnapshotJson());
            StringBuilder content = new StringBuilder();
            Disposable execution = harness.stream(kernel, providerConfig,
                            new MangaAgentContext(run.getUserId(), run.getProjectId()), run.getStateSessionId(), userMessage)
                    .doOnNext(event -> appendScopeEvent(runId, event, content))
                    .doOnError(error -> fail(runId, "AGENT_EXECUTION_FAILED", safeMessage(error)))
                    .doOnComplete(() -> complete(runId, content.toString()))
                    .subscribe();
            activeExecutions.put(runId, execution);
        } catch (RuntimeException exception) {
            fail(runId, "AGENT_START_FAILED", safeMessage(exception));
        }
    }

    private void appendScopeEvent(String runId, AgentEvent event, StringBuilder content) {
        if (event instanceof TextBlockDeltaEvent textDelta) {
            String delta = textDelta.getDelta();
            if (!delta.isEmpty()) {
                content.append(delta);
                eventService.append(runId, AgentEventType.CONTENT, Map.of("delta", delta));
            }
        } else if (event instanceof ToolCallStartEvent toolCall) {
            eventService.append(runId, AgentEventType.TOOL_CALL_STARTED, Map.of("toolName", toolCall.getToolCallName()));
        } else if (event instanceof ToolResultEndEvent toolResult) {
            eventService.append(runId, AgentEventType.TOOL_CALL_FINISHED, Map.of("toolName", toolResult.getToolCallName()));
        }
        renewLease(runId);
    }

    public void cancel(String runId, long userId) {
        transactionTemplate.executeWithoutResult(status -> {
            AgentRun run = requireOwned(runId, userId);
            if (run.getStatus() != AgentRunStatus.RUNNING) {
                return;
            }
            run.setStatus(AgentRunStatus.CANCELLED);
            run.setActiveConversationId(null);
            run.setFinishedAt(LocalDateTime.now());
            run.setLeaseUntil(null);
            runRepository.update(run);
            eventService.append(runId, AgentEventType.RUN_CANCELLED, Map.of("message", "用户已取消助手运行"));
            Optional.ofNullable(activeExecutions.remove(runId)).ifPresent(Disposable::dispose);
        });
    }

    public AgentRunResponse findRun(String runId, long userId) {
        return toResponse(requireOwned(runId, userId));
    }

    public List<AgentConversationResponse> listConversations(long userId, Long projectId) {
        return conversationRepository.findByUser(userId, projectId).stream()
                .map(conversation -> new AgentConversationResponse(conversation.getConversationId(), conversation.getProjectId(),
                        conversation.getTitle(), conversation.getStatus(), conversation.getUpdatedAt()))
                .toList();
    }

    public List<AgentMessageResponse> listMessages(String conversationId, long userId) {
        requireConversationOwned(conversationId, userId);
        return messageRepository.findByConversation(conversationId).stream()
                .map(message -> new AgentMessageResponse(message.getRole(), message.getContent(), message.getMessageOrder(),
                        message.getCreatedAt()))
                .toList();
    }

    @Scheduled(fixedDelay = 5000)
    public void renewActiveLeases() {
        activeExecutions.keySet().forEach(this::renewLease);
    }

    @Scheduled(fixedDelay = 10000)
    public void failExpiredRuns() {
        for (AgentRun run : runRepository.findExpiredRunning(LocalDateTime.now())) {
            fail(run.getRunId(), "AGENT_LEASE_EXPIRED", "助手运行因实例中断而结束");
        }
    }

    void complete(String runId, String content) {
        transactionTemplate.executeWithoutResult(status -> {
            AgentRun run = requireRun(runId);
            if (run.getStatus() != AgentRunStatus.RUNNING) {
                return;
            }
            if (!content.isBlank()) {
                saveMessage(run.getConversationId(), runId, "ASSISTANT", content);
            }
            run.setStatus(AgentRunStatus.COMPLETED);
            run.setActiveConversationId(null);
            run.setFinishedAt(LocalDateTime.now());
            run.setLeaseUntil(null);
            runRepository.update(run);
            eventService.append(runId, AgentEventType.RUN_COMPLETED, Map.of());
            activeExecutions.remove(runId);
        });
    }

    void fail(String runId, String code, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            AgentRun run = requireRun(runId);
            if (run.getStatus() != AgentRunStatus.RUNNING) {
                return;
            }
            run.setStatus(AgentRunStatus.FAILED);
            run.setActiveConversationId(null);
            run.setErrorCode(code);
            run.setErrorMessage(message);
            run.setFinishedAt(LocalDateTime.now());
            run.setLeaseUntil(null);
            runRepository.update(run);
            eventService.append(runId, AgentEventType.RUN_FAILED, Map.of("code", code, "message", message));
            activeExecutions.remove(runId);
        });
    }

    void renewLease(String runId) {
        transactionTemplate.executeWithoutResult(status -> {
            AgentRun run = runRepository.lockByRunId(runId);
            if (run != null && run.getStatus() == AgentRunStatus.RUNNING) {
                run.setLeaseUntil(LocalDateTime.now().plus(properties.getRuntime().getOwnerLease()));
                runRepository.update(run);
            }
        });
    }

    private AgentConversation resolveConversation(long userId, String conversationId, Long projectId, String message) {
        if (conversationId != null && !conversationId.isBlank()) {
            AgentConversation existing = requireConversationOwned(conversationId, userId);
            if (projectId != null && existing.getProjectId() != null && !projectId.equals(existing.getProjectId())) {
                throw new BusinessException(CommonResponseCode.VALIDATION_ERROR, "会话已绑定其他项目", HttpStatus.BAD_REQUEST);
            }
            return existing;
        }
        return conversationRepository.create(AgentConversation.builder()
                .conversationId(UUID.randomUUID().toString())
                .userId(userId)
                .projectId(projectId)
                .title(title(message))
                .status("ACTIVE")
                .build());
    }

    private void saveMessage(String conversationId, String runId, String role, String content) {
        messageRepository.create(AgentMessage.builder()
                .conversationId(conversationId)
                .runId(runId)
                .role(role)
                .content(content)
                .messageOrder(messageRepository.nextOrder(conversationId))
                .build());
    }

    private MangaKernelSpec buildKernel(ResolvedAiProviderConfig config) {
        Map<String, Object> fingerprintMaterial = new LinkedHashMap<>();
        fingerprintMaterial.put("agentKey", AGENT_KEY);
        fingerprintMaterial.put("providerConfigId", config.id());
        fingerprintMaterial.put("providerType", config.providerType().name());
        fingerprintMaterial.put("baseUrl", config.baseUrl());
        fingerprintMaterial.put("modelCode", config.defaultModel());
        fingerprintMaterial.put("systemPrompt", SYSTEM_PROMPT);
        fingerprintMaterial.put("tools", List.of(MangaProjectContextTool.NAME));
        String fingerprint = sha256(write(fingerprintMaterial));
        return new MangaKernelSpec(AGENT_KEY, config.id(), config.providerType(), config.baseUrl(), config.defaultModel(),
                SYSTEM_PROMPT, List.of(MangaProjectContextTool.NAME), properties.getRuntime().getMaxIterations(), fingerprint);
    }

    private MangaKernelSpec readKernel(String json) {
        try {
            return objectMapper.readValue(json, MangaKernelSpec.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("助手运行配置快照损坏", exception);
        }
    }

    private AgentRun requireOwned(String runId, long userId) {
        return runRepository.findOwned(runId, userId)
                .orElseThrow(() -> new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN));
    }

    private AgentRun requireRun(String runId) {
        AgentRun run = runRepository.lockByRunId(runId);
        if (run == null) {
            throw new IllegalArgumentException("助手运行不存在");
        }
        return run;
    }

    private AgentConversation requireConversationOwned(String conversationId, long userId) {
        return conversationRepository.findOwned(conversationId, userId)
                .orElseThrow(() -> new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN));
    }

    private AgentRunResponse toResponse(AgentRun run) {
        return new AgentRunResponse(run.getRunId(), run.getConversationId(), run.getProjectId(), run.getStatus(),
                run.getStartedAt(), run.getFinishedAt(), run.getErrorCode(), run.getErrorMessage());
    }

    private String write(Object source) {
        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("助手配置序列化失败", exception);
        }
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private String title(String message) {
        String normalized = message.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private String safeMessage(Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        return "助手运行失败，请检查 AI 配置后重试";
    }

    private record StartedRun(AgentRun run, String message) {
    }
}
