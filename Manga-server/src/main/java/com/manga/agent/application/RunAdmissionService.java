package com.manga.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.api.CreativeRuntimeResponseCode;
import com.manga.agent.blueprint.AgentBlueprintCompileRequest;
import com.manga.agent.blueprint.AgentBlueprintCompiler;
import com.manga.agent.blueprint.CompiledAgentBlueprint;
import com.manga.agent.config.CreativeRuntimeProperties;
import com.manga.agent.domain.CreativeActor;
import com.manga.agent.domain.CreativeConversationStatus;
import com.manga.agent.domain.CreativeDispatchOutboxStatus;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeRunRequestFingerprintMaterial;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeRunTriggerSource;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.domain.CreativeStepInput;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.domain.CreativeStepType;
import com.manga.agent.domain.CreativeWorkflowType;
import com.manga.agent.event.CreativeEventSources;
import com.manga.agent.event.RunAcceptedEventPayload;
import com.manga.agent.event.StepCreatedEventPayload;
import com.manga.agent.execution.CreativeStepDispatcher;
import com.manga.agent.profile.AgentOperationBinding;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeConversation;
import com.manga.entity.CreativeDispatchOutbox;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.repository.CreativeConversationRepository;
import com.manga.repository.CreativeDispatchOutboxRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import com.manga.repository.ProjectScriptRepository;
import com.manga.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * 在一个事务内完成 Run 受理、根 Step 创建、事件日志和调度 Outbox 落库。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RunAdmissionService {

    /** 从用户指令生成会话标题时允许保留的最大字符数。 */
    private static final int CONVERSATION_TITLE_MAX_LENGTH = 80;
    /** 根 Step 因租约过期而重新领取时允许的最大累计执行次数。 */
    private static final int ROOT_STEP_MAX_ATTEMPTS = 3;

    private final CreativeConversationRepository conversationRepository;
    private final CreativeRunRepository runRepository;
    private final CreativeStepRepository stepRepository;
    private final CreativeDispatchOutboxRepository dispatchOutboxRepository;
    private final CreativeEventService eventService;
    private final ProjectService projectService;
    private final CreativeRuntimeProperties properties;
    private final CreativeIdentifierGenerator identifierGenerator;
    private final CreativeStepDispatcher stepDispatcher;
    private final AgentBlueprintCompiler blueprintCompiler;
    private final ProjectScriptRepository scriptRepository;
    private final ObjectMapper objectMapper;
    private final CreativeConversationMemory conversationMemory;
    private final AgentStateSessionService stateSessions;

    /**
     * 幂等受理会话或手动创作请求。新请求会原子创建 Run、READY 根 Step 和 Dispatch Outbox，
     * 仅在事务提交成功后向本实例执行器发送低延迟调度信号。READ_COMMITTED 保证等待会话锁后，
     * 上下文读取能够看到刚结束的上一轮消息，避免 MySQL 可重复读的旧快照遗漏最近回答。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreativeRunStartResult admit(StartCreativeRunCommand command) {
        String requestFingerprint = requestFingerprint(command);
        CreativeRun existing = runRepository.findByRequestId(command.userId(), command.requestId()).orElse(null);
        if (Objects.nonNull(existing)) {
            requireSameRequest(existing, requestFingerprint);
            return toResult(existing);
        }
        if (Objects.nonNull(command.projectId())) {
            projectService.requireAccessibleProject(command.projectId(), command.userId());
        }
        CreativeConversation conversation = resolveConversation(command);
        Long projectId = resolveProjectId(command, conversation);
        if (Objects.nonNull(projectId) && !projectId.equals(command.projectId())) {
            projectService.requireAccessibleProject(projectId, command.userId());
        }
        AgentStepConfiguration agentStep = resolveAgentStep(command, projectId);
        CreativeRun run = createRun(command, conversation, projectId, requestFingerprint);
        String executionInstruction = conversationMemory.compose(conversation, command.instruction());
        try {
            runRepository.create(run);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(CreativeRuntimeResponseCode.ACTIVE_RUN_CONFLICT, HttpStatus.CONFLICT);
        }
        CreativeStep rootStep = createRootStep(command, run, agentStep, executionInstruction);
        stepRepository.create(rootStep);
        if (Objects.nonNull(agentStep)) {
            stateSessions.register(run, agentStep.stateSessionId());
        }
        dispatchOutboxRepository.create(CreativeDispatchOutbox.builder()
                .stepId(rootStep.getStepId())
                .scheduleVersion(rootStep.getScheduleVersion())
                .status(CreativeDispatchOutboxStatus.PENDING)
                .attempts(0)
                .createdBy(rootStep.getCreatedBy())
                .updatedBy(rootStep.getUpdatedBy())
                .build());
        eventService.append(run.getRunId(), CreativeEventType.RUN_ACCEPTED,
                new RunAcceptedEventPayload(command.requestId(), command.triggerSource(), command.operationType(), command.instruction()));
        eventService.append(run.getRunId(), rootStep.getStepId(), CreativeEventType.STEP_CREATED,
                CreativeEventSources.RUNTIME, null,
                new StepCreatedEventPayload(rootStep.getStepId(), rootStep.getStepType(),
                        rootStep.getScheduleVersion()));
        dispatchAfterCommit(rootStep);
        log.info("Creative Run admitted runId={} rootStepId={} userId={} projectId={} triggerSource={} operationType={}",
                run.getRunId(), rootStep.getStepId(), command.userId(), projectId,
                command.triggerSource(), command.operationType());
        return toResult(run);
    }

    /** 创建初始 READY 根 Step，并将类型化输入序列化为 JSON。 */
    private CreativeStep createRootStep(
            StartCreativeRunCommand command,
            CreativeRun run,
            AgentStepConfiguration agentStep,
            String executionInstruction) {
        String actor = CreativeActor.user(command.userId());
        return CreativeStep.builder()
                .stepId(identifierGenerator.nextStepId())
                .runId(run.getRunId())
                .stepType(Objects.isNull(agentStep)
                        ? CreativeStepType.ROOT_WORKFLOW
                        : CreativeStepType.AGENT_INVOCATION)
                .status(CreativeStepStatus.READY)
                .scheduleVersion(1L)
                .ownerEpoch(0L)
                .attemptNo(0)
                .maxAttempts(ROOT_STEP_MAX_ATTEMPTS)
                .inputJson(write(new CreativeStepInput(executionInstruction, command.operationType(), command.target())))
                .blueprintSnapshotJson(Objects.isNull(agentStep) ? null : agentStep.blueprint().snapshotJson())
                .blueprintFingerprint(Objects.isNull(agentStep)
                        ? null
                        : agentStep.blueprint().blueprint().fingerprint())
                .stateSessionId(Objects.isNull(agentStep) ? null : agentStep.stateSessionId())
                .createdBy(actor)
                .updatedBy(actor)
                .build();
    }

    /**
     * 为真实 Agent 操作校验目标资源并冻结执行 Blueprint；非 Agent 操作继续使用阶段一执行器。
     */
    private AgentStepConfiguration resolveAgentStep(StartCreativeRunCommand command, Long projectId) {
        AgentOperationBinding binding = AgentOperationBinding.find(command.operationType()).orElse(null);
        if (Objects.isNull(binding)) {
            return null;
        }
        if (Objects.nonNull(binding.targetType())
                && (Objects.isNull(command.target()) || command.target().resourceType() != binding.targetType())) {
            throw new BusinessException(
                    CreativeRuntimeResponseCode.TARGET_RESOURCE_INVALID,
                    CreativeRuntimeMessages.AGENT_OPERATION_TARGET_TYPE_INVALID_TEMPLATE
                            .formatted(command.operationType(), binding.targetType()),
                    HttpStatus.BAD_REQUEST);
        }
        boolean validScript = Objects.nonNull(projectId)
                && Objects.nonNull(command.target())
                && scriptRepository.findByProjectId(projectId)
                .filter(script -> Objects.equals(script.getId(), command.target().resourceId()))
                .isPresent();
        if (Objects.nonNull(binding.targetType()) && !validScript) {
            throw new BusinessException(
                    CreativeRuntimeResponseCode.TARGET_RESOURCE_INVALID,
                    CreativeRuntimeMessages.AGENT_OPERATION_SCRIPT_TARGET_INVALID,
                    HttpStatus.BAD_REQUEST);
        }
        CompiledAgentBlueprint compiled = blueprintCompiler.compile(new AgentBlueprintCompileRequest(
                command.userId(),
                binding.profileKey(),
                command.permissionPolicy(),
                null,
                Map.of()
        ));
        return new AgentStepConfiguration(compiled, identifierGenerator.nextStateSessionId());
    }

    /** 注册事务提交回调，避免执行器读取到尚未提交的 Step。 */
    private void dispatchAfterCommit(CreativeStep step) {
        Runnable dispatch = () -> stepDispatcher.dispatchAsync(step.getStepId(), step.getScheduleVersion());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
            return;
        }
        dispatch.run();
    }

    /** 复用已有会话，或为新的会话触发请求创建会话。 */
    private CreativeConversation resolveConversation(StartCreativeRunCommand command) {
        if (command.triggerSource() != CreativeRunTriggerSource.CONVERSATION) {
            return null;
        }
        if (StringUtils.hasText(command.conversationId())) {
            CreativeConversation conversation = conversationRepository
                    .lockOwned(command.conversationId(), command.userId())
                    .orElseThrow(() -> new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN));
            bindProjectIfNecessary(conversation, command.projectId(), command.userId());
            return conversation;
        }
        String actor = CreativeActor.user(command.userId());
        return conversationRepository.create(CreativeConversation.builder()
                .conversationId(identifierGenerator.nextConversationId())
                .userId(command.userId())
                .projectId(command.projectId())
                .title(title(command.instruction()))
                .status(CreativeConversationStatus.ACTIVE)
                .stateGeneration(1L)
                .summarizedThroughMessageId(0L)
                .createdBy(actor)
                .updatedBy(actor)
                .build());
    }

    /** 首次显式绑定项目时更新会话，拒绝把已有会话切换到其他项目。 */
    private void bindProjectIfNecessary(CreativeConversation conversation, Long requestedProjectId, long userId) {
        if (Objects.isNull(requestedProjectId)) {
            return;
        }
        if (Objects.nonNull(conversation.getProjectId()) && !requestedProjectId.equals(conversation.getProjectId())) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR,
                    CreativeRuntimeMessages.CONVERSATION_PROJECT_CONFLICT, HttpStatus.BAD_REQUEST);
        }
        if (Objects.isNull(conversation.getProjectId())) {
            conversation.setProjectId(requestedProjectId);
            conversation.setUpdatedBy(CreativeActor.user(userId));
            conversationRepository.update(conversation);
        }
    }

    /** 解析本次 Run 最终绑定的项目主键。 */
    private Long resolveProjectId(StartCreativeRunCommand command, CreativeConversation conversation) {
        return Objects.nonNull(command.projectId()) ? command.projectId()
                : Objects.isNull(conversation) ? null : conversation.getProjectId();
    }

    /** 构造受理阶段的 QUEUED Run。 */
    private CreativeRun createRun(StartCreativeRunCommand command, CreativeConversation conversation, Long projectId,
            String requestFingerprint) {
        LocalDateTime now = LocalDateTime.now();
        String actor = CreativeActor.user(command.userId());
        return CreativeRun.builder()
                .runId(identifierGenerator.nextRunId())
                .requestId(command.requestId())
                .requestFingerprint(requestFingerprint)
                .conversationId(Objects.isNull(conversation) ? null : conversation.getConversationId())
                .userId(command.userId())
                .projectId(projectId)
                .engineVersion(properties.engineVersion())
                .workflowType(CreativeWorkflowType.fromOperation(command.operationType()))
                .workflowVersion(properties.workflowVersion())
                .permissionPolicy(command.permissionPolicy())
                .triggerSource(command.triggerSource())
                .operationType(command.operationType())
                .targetType(Objects.isNull(command.target()) ? null : command.target().resourceType())
                .targetId(Objects.isNull(command.target()) ? null : command.target().resourceId())
                .status(CreativeRunStatus.QUEUED)
                .activeConversationId(Objects.isNull(conversation) ? null : conversation.getConversationId())
                .ownerEpoch(0L)
                .deadlineAt(now.plus(properties.runTimeout()))
                .nextSequence(1L)
                .projectedThroughSequence(0L)
                .stateGeneration(Objects.isNull(conversation) ? 1L : conversation.getStateGeneration())
                .createdBy(actor)
                .updatedBy(actor)
                .build();
    }

    /** 同一幂等键只能重放语义完全相同的创建请求。 */
    private void requireSameRequest(CreativeRun existing, String requestFingerprint) {
        if (!requestFingerprint.equals(existing.getRequestFingerprint())) {
            throw new BusinessException(CreativeRuntimeResponseCode.IDEMPOTENCY_KEY_CONFLICT, HttpStatus.CONFLICT);
        }
    }

    /** 将类型化请求材料序列化并计算稳定 SHA-256 指纹。 */
    private String requestFingerprint(StartCreativeRunCommand command) {
        CreativeRunRequestFingerprintMaterial material = new CreativeRunRequestFingerprintMaterial(
                command.triggerSource(), command.operationType(), command.conversationId(), command.projectId(),
                command.target(), command.permissionPolicy(), command.instruction());
        try {
            byte[] json = objectMapper.writeValueAsBytes(material);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(CreativeRuntimeMessages.REQUEST_FINGERPRINT_SERIALIZATION_FAILED, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(CreativeRuntimeMessages.SHA_256_UNAVAILABLE, exception);
        }
    }

    /** 序列化稳定 Step 输入。 */
    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(CreativeRuntimeMessages.EVENT_PAYLOAD_SERIALIZATION_FAILED, exception);
        }
    }

    /** 将持久化 Run 投影为运行时门面返回值。 */
    private CreativeRunStartResult toResult(CreativeRun run) {
        return new CreativeRunStartResult(run.getRunId(), run.getConversationId(), run.getProjectId(),
                run.getStatus(), run.getStartedAt(), run.getFinishedAt(), run.getErrorCode(), run.getErrorMessage());
    }

    /** 从用户指令生成长度受控的会话标题。 */
    private String title(String instruction) {
        String normalized = instruction.trim().replaceAll("\\s+", " ");
        return normalized.length() <= CONVERSATION_TITLE_MAX_LENGTH
                ? normalized : normalized.substring(0, CONVERSATION_TITLE_MAX_LENGTH);
    }

    /** 保存创建 Agent Step 所需的不可变配置。 */
    private record AgentStepConfiguration(
            /** 已编译且不含密钥的 Blueprint。 */ CompiledAgentBlueprint blueprint,
            /** AgentScope 状态隔离会话标识。 */ String stateSessionId
    ) {
    }
}
