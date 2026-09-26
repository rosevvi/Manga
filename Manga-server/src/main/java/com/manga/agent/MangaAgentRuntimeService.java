package com.manga.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.domain.BuiltInAgentDefinition;
import com.manga.agent.domain.AgentConversationStatus;
import com.manga.agent.domain.AgentMessageRole;
import com.manga.agent.domain.AgentRunFailureCode;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeRunTriggerSource;
import com.manga.agent.domain.CreativeRuntimeType;
import com.manga.agent.domain.MangaKernelFingerprintMaterial;
import com.manga.agent.domain.RuntimeInstanceIdentity;
import com.manga.agent.event.ContentEventPayload;
import com.manga.agent.event.RunFailedEventPayload;
import com.manga.agent.event.RunMessageEventPayload;
import com.manga.agent.event.RunStartedEventPayload;
import com.manga.agent.event.ToolCallEventPayload;
import com.manga.common.constant.AsyncConstants;
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
import java.util.Objects;
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

    private static final BuiltInAgentDefinition AGENT_DEFINITION = BuiltInAgentDefinition.MANGA_DIRECTOR;
    private static final String STATE_SESSION_KEY_FORMAT = "manga:agent:%s:%s";

    private final AgentConversationRepository conversationRepository;
    private final AgentRunRepository runRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentEventService eventService;
    private final AiProviderConfigService providerConfigService;
    private final MangaAgentModelFactory modelFactory;
    private final MangaAgentHarness harness;
    private final ProjectService projectService;
    private final MangaAgentProperties properties;
    private final RuntimeInstanceIdentity runtimeInstanceIdentity;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    @Qualifier(AsyncConstants.MANGA_COMMON_TASK_EXECUTOR)
    private final Executor executor;

    private final ConcurrentMap<String, Disposable> activeExecutions = new ConcurrentHashMap<>();

    /** 创建并异步启动一条 Legacy 助手会话 Run。 */
    public AgentRunResponse start(long userId, AgentRunCreateRequest request) {
        StartedRun started = transactionTemplate.execute(status -> createRun(userId, request));
        if (Objects.isNull(started)) {
            throw new IllegalStateException("助手运行创建事务未返回结果");
        }
        executor.execute(() -> execute(started.run().getRunId(), started.message()));
        return toResponse(started.run());
    }

    /** 在短事务中完成权限检查、快照固化以及 Run、消息和起始事件持久化。 */
    private StartedRun createRun(long userId, AgentRunCreateRequest request) {
        Long requestedProjectId = request.projectId();
        if (Objects.nonNull(requestedProjectId)) {
            projectService.requireAccessibleProject(requestedProjectId, userId);
        }
        AgentConversation conversation = resolveConversation(userId, request.conversationId(), requestedProjectId, request.message());
        Long projectId = Objects.isNull(requestedProjectId) ? conversation.getProjectId() : requestedProjectId;
        if (Objects.nonNull(projectId)) {
            projectService.requireAccessibleProject(projectId, userId);
        }
        ResolvedAiProviderConfig providerConfig = providerConfigService.resolveDefault(userId)
                .orElseThrow(() -> new BusinessException(CommonResponseCode.VALIDATION_ERROR,
                        "请先在 AI 配置中保存并启用默认文本模型", HttpStatus.UNPROCESSABLE_ENTITY));
        if (!modelFactory.isOpenAiCompatible(providerConfig.providerType())) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR,
                    "当前默认 AI 配置不是 OpenAI 兼容文本服务", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (Objects.isNull(providerConfig.defaultModel()) || providerConfig.defaultModel().isBlank()) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR,
                    "当前默认 AI 配置缺少默认模型", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String runId = UUID.randomUUID().toString();
        String stateSessionId = STATE_SESSION_KEY_FORMAT.formatted(
                conversation.getConversationId(), AGENT_DEFINITION.key());
        MangaKernelSpec kernel = buildKernel(providerConfig);
        LocalDateTime now = LocalDateTime.now();
        AgentRun run = AgentRun.builder()
                .runId(runId)
                .conversationId(conversation.getConversationId())
                .userId(userId)
                .projectId(projectId)
                .runtimeType(CreativeRuntimeType.LEGACY)
                .triggerSource(CreativeRunTriggerSource.CONVERSATION)
                .operationType(CreativeOperationType.ASSISTANT_CHAT)
                .agentKey(AGENT_DEFINITION.key())
                .providerConfigId(providerConfig.id())
                .modelCode(providerConfig.defaultModel())
                .kernelFingerprint(kernel.fingerprint())
                .kernelSnapshotJson(write(kernel))
                .stateSessionId(stateSessionId)
                .status(AgentRunStatus.RUNNING)
                .activeConversationId(conversation.getConversationId())
                .ownerInstanceId(runtimeInstanceIdentity.value())
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
        saveMessage(conversation.getConversationId(), runId, AgentMessageRole.USER, request.message().trim());
        eventService.append(runId, AgentEventType.RUN_STARTED,
                new RunStartedEventPayload(conversation.getConversationId()));
        return new StartedRun(run, request.message().trim());
    }

    /** 在事务外调用 AgentScope，并把模型和工具事件持续投影到运行事件日志。 */
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
                    .doOnError(error -> fail(runId, AgentRunFailureCode.AGENT_EXECUTION_FAILED, safeMessage(error)))
                    .doOnComplete(() -> complete(runId, content.toString()))
                    .subscribe();
            activeExecutions.put(runId, execution);
        } catch (RuntimeException exception) {
            fail(runId, AgentRunFailureCode.AGENT_START_FAILED, safeMessage(exception));
        }
    }

    /** 将 AgentScope 原生事件转换为稳定的 Manga Run 事件，并刷新执行租约。 */
    private void appendScopeEvent(String runId, AgentEvent event, StringBuilder content) {
        if (event instanceof TextBlockDeltaEvent textDelta) {
            String delta = textDelta.getDelta();
            if (!delta.isEmpty()) {
                content.append(delta);
                eventService.append(runId, AgentEventType.CONTENT, new ContentEventPayload(delta));
            }
        } else if (event instanceof ToolCallStartEvent toolCall) {
            eventService.append(runId, AgentEventType.TOOL_CALL_STARTED,
                    new ToolCallEventPayload(toolCall.getToolCallName()));
        } else if (event instanceof ToolResultEndEvent toolResult) {
            eventService.append(runId, AgentEventType.TOOL_CALL_FINISHED,
                    new ToolCallEventPayload(toolResult.getToolCallName()));
        }
        renewLease(runId);
    }

    /** 取消用户拥有的运行，并立即释放当前进程中的流式订阅。 */
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
            eventService.append(runId, AgentEventType.RUN_CANCELLED,
                    new RunMessageEventPayload("用户已取消助手运行"));
            Optional.ofNullable(activeExecutions.remove(runId)).ifPresent(Disposable::dispose);
        });
    }

    /** 查询用户拥有的运行摘要。 */
    public AgentRunResponse findRun(String runId, long userId) {
        return toResponse(requireOwned(runId, userId));
    }

    /** 查询用户的助手会话，可按项目过滤。 */
    public List<AgentConversationResponse> listConversations(long userId, Long projectId) {
        return conversationRepository.findByUser(userId, projectId).stream()
                .map(conversation -> new AgentConversationResponse(conversation.getConversationId(), conversation.getProjectId(),
                        conversation.getTitle(), conversation.getStatus(), conversation.getUpdatedAt()))
                .toList();
    }

    /** 查询用户拥有的指定会话消息。 */
    public List<AgentMessageResponse> listMessages(String conversationId, long userId) {
        requireConversationOwned(conversationId, userId);
        return messageRepository.findByConversation(conversationId).stream()
                .map(message -> new AgentMessageResponse(message.getRole(), message.getContent(), message.getMessageOrder(),
                        message.getCreatedAt()))
                .toList();
    }

    /** 周期性续租当前进程仍在执行的 Legacy Run。 */
    @Scheduled(fixedDelay = 5000)
    public void renewActiveLeases() {
        activeExecutions.keySet().forEach(this::renewLease);
    }

    /** 将已失去执行实例且租约过期的 Legacy Run 明确收敛为失败。 */
    @Scheduled(fixedDelay = 10000)
    public void failExpiredRuns() {
        for (AgentRun run : runRepository.findExpiredRunning(LocalDateTime.now())) {
            fail(run.getRunId(), AgentRunFailureCode.AGENT_LEASE_EXPIRED, "助手运行因实例中断而结束");
        }
    }

    /** 在事务内保存助手消息并完成仍处于运行中的 Run。 */
    void complete(String runId, String content) {
        transactionTemplate.executeWithoutResult(status -> {
            AgentRun run = requireRun(runId);
            if (run.getStatus() != AgentRunStatus.RUNNING) {
                return;
            }
            if (!content.isBlank()) {
                saveMessage(run.getConversationId(), runId, AgentMessageRole.ASSISTANT, content);
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

    /** 在事务内记录安全错误摘要并终止仍处于运行中的 Run。 */
    void fail(String runId, AgentRunFailureCode failureCode, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            AgentRun run = requireRun(runId);
            if (run.getStatus() != AgentRunStatus.RUNNING) {
                return;
            }
            run.setStatus(AgentRunStatus.FAILED);
            run.setActiveConversationId(null);
            run.setErrorCode(failureCode.name());
            run.setErrorMessage(message);
            run.setFinishedAt(LocalDateTime.now());
            run.setLeaseUntil(null);
            runRepository.update(run);
            eventService.append(runId, AgentEventType.RUN_FAILED,
                    new RunFailedEventPayload(failureCode.name(), message));
            activeExecutions.remove(runId);
        });
    }

    /** 通过行锁刷新仍处于运行状态的 Run 租约。 */
    void renewLease(String runId) {
        transactionTemplate.executeWithoutResult(status -> {
            AgentRun run = runRepository.lockByRunId(runId);
            if (Objects.nonNull(run) && run.getStatus() == AgentRunStatus.RUNNING) {
                run.setLeaseUntil(LocalDateTime.now().plus(properties.getRuntime().getOwnerLease()));
                runRepository.update(run);
            }
        });
    }

    /** 复用用户已有会话，或为首条消息创建新会话。 */
    private AgentConversation resolveConversation(long userId, String conversationId, Long projectId, String message) {
        if (Objects.nonNull(conversationId) && !conversationId.isBlank()) {
            AgentConversation existing = requireConversationOwned(conversationId, userId);
            if (Objects.nonNull(projectId) && Objects.nonNull(existing.getProjectId())
                    && !projectId.equals(existing.getProjectId())) {
                throw new BusinessException(CommonResponseCode.VALIDATION_ERROR, "会话已绑定其他项目", HttpStatus.BAD_REQUEST);
            }
            return existing;
        }
        return conversationRepository.create(AgentConversation.builder()
                .conversationId(UUID.randomUUID().toString())
                .userId(userId)
                .projectId(projectId)
                .title(title(message))
                .status(AgentConversationStatus.ACTIVE.name())
                .build());
    }

    /** 按会话内稳定顺序保存一条用户或助手消息。 */
    private void saveMessage(String conversationId, String runId, AgentMessageRole role, String content) {
        messageRepository.create(AgentMessage.builder()
                .conversationId(conversationId)
                .runId(runId)
                .role(role.name())
                .content(content)
                .messageOrder(messageRepository.nextOrder(conversationId))
                .build());
    }

    /** 根据内置 Agent 定义和当前模型配置生成不可变 Kernel 快照及指纹。 */
    private MangaKernelSpec buildKernel(ResolvedAiProviderConfig config) {
        int maxIterations = properties.getRuntime().getMaxIterations();
        List<String> toolWhitelist = List.of(MangaProjectContextTool.NAME);
        MangaKernelFingerprintMaterial material = new MangaKernelFingerprintMaterial(
                AGENT_DEFINITION.key(), AGENT_DEFINITION.displayName(), AGENT_DEFINITION.description(), config.id(),
                config.providerType(), config.baseUrl(), config.defaultModel(), AGENT_DEFINITION.systemPrompt(),
                toolWhitelist, maxIterations);
        Map<String, Object> fingerprintMaterial = objectMapper.convertValue(material,
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
        String fingerprint = sha256(write(fingerprintMaterial));
        return new MangaKernelSpec(AGENT_DEFINITION.key(), AGENT_DEFINITION.displayName(),
                AGENT_DEFINITION.description(), config.id(), config.providerType(), config.baseUrl(),
                config.defaultModel(), AGENT_DEFINITION.systemPrompt(), toolWhitelist, maxIterations, fingerprint);
    }

    /** 从 Run 中恢复创建时固化的 Kernel 快照。 */
    private MangaKernelSpec readKernel(String json) {
        try {
            return objectMapper.readValue(json, MangaKernelSpec.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("助手运行配置快照损坏", exception);
        }
    }

    /** 返回用户拥有的 Run，不存在或越权时统一拒绝。 */
    private AgentRun requireOwned(String runId, long userId) {
        return runRepository.findOwned(runId, userId)
                .orElseThrow(() -> new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN));
    }

    /** 加行锁读取 Run，供状态迁移和事件序号分配使用。 */
    private AgentRun requireRun(String runId) {
        AgentRun run = runRepository.lockByRunId(runId);
        if (Objects.isNull(run)) {
            throw new IllegalArgumentException("助手运行不存在");
        }
        return run;
    }

    /** 返回用户拥有的会话，不存在或越权时统一拒绝。 */
    private AgentConversation requireConversationOwned(String conversationId, long userId) {
        return conversationRepository.findOwned(conversationId, userId)
                .orElseThrow(() -> new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN));
    }

    /** 将持久化实体转换为稳定接口摘要。 */
    private AgentRunResponse toResponse(AgentRun run) {
        return new AgentRunResponse(run.getRunId(), run.getConversationId(), run.getProjectId(), run.getStatus(),
                run.getStartedAt(), run.getFinishedAt(), run.getErrorCode(), run.getErrorMessage());
    }

    /** 使用统一 ObjectMapper 将快照或事件内容序列化为 JSON。 */
    private String write(Object source) {
        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("助手配置序列化失败", exception);
        }
    }

    /** 计算规范化 Kernel 材料的 SHA-256 指纹。 */
    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    /** 从首条用户消息生成长度受控的会话标题。 */
    private String title(String message) {
        String normalized = message.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    /** 将内部异常转换为可安全持久化和展示的错误摘要。 */
    private String safeMessage(Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        return "助手运行失败，请检查 AI 配置后重试";
    }

    private record StartedRun(
            /** 已持久化的运行记录。 */
            AgentRun run,
            /** 去除首尾空白后的用户消息。 */
            String message
    ) {
    }
}
