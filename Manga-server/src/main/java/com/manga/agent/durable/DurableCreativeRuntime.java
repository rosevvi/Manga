package com.manga.agent.durable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.api.CreativeRuntimeResponseCode;
import com.manga.agent.application.CancelCreativeRunCommand;
import com.manga.agent.application.CreativeEventService;
import com.manga.agent.application.CreativeIdentifierGenerator;
import com.manga.agent.application.CreativeRunStartResult;
import com.manga.agent.application.CreativeRuntime;
import com.manga.agent.application.StartCreativeRunCommand;
import com.manga.agent.config.CreativeRuntimeProperties;
import com.manga.agent.domain.CreativeActor;
import com.manga.agent.domain.CreativeConversationStatus;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeRunStateMachine;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeRunRequestFingerprintMaterial;
import com.manga.agent.domain.CreativeRunTriggerSource;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.domain.CreativeWorkflowType;
import com.manga.agent.event.RunAcceptedEventPayload;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeConversation;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeConversationRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 负责 Creative Run 的幂等受理与取消；实际 Step 调度由后续阶段接入。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DurableCreativeRuntime implements CreativeRuntime {

    private static final int CONVERSATION_TITLE_MAX_LENGTH = 80;

    private final CreativeConversationRepository conversationRepository;
    private final CreativeRunRepository runRepository;
    private final CreativeEventService eventService;
    private final ProjectService projectService;
    private final CreativeRuntimeProperties properties;
    private final CreativeIdentifierGenerator identifierGenerator;
    private final ObjectMapper objectMapper;

    /** 幂等受理会话或手动创作请求，并将新 Run 持久化为 QUEUED。 */
    @Override
    @Transactional
    public CreativeRunStartResult start(StartCreativeRunCommand command) {
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
        CreativeRun run = createRun(command, conversation, projectId, requestFingerprint);
        try {
            runRepository.create(run);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(CreativeRuntimeResponseCode.ACTIVE_RUN_CONFLICT, HttpStatus.CONFLICT);
        }
        eventService.append(run.getRunId(), CreativeEventType.RUN_ACCEPTED,
                new RunAcceptedEventPayload(command.requestId(), command.triggerSource(), command.operationType()));
        log.info("Creative Run accepted runId={} userId={} projectId={} triggerSource={} operationType={}",
                run.getRunId(), command.userId(), projectId, command.triggerSource(), command.operationType());
        return toResult(run);
    }

    /** 将用户拥有的非终态 Run 收敛为 CANCELLED，并写入请求和终态事件。 */
    @Override
    @Transactional
    public void cancel(CancelCreativeRunCommand command) {
        CreativeRun run = runRepository.lockOwned(command.runId(), command.userId())
                .orElseThrow(() -> new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN));
        if (run.getStatus().isTerminal()) {
            return;
        }
        String actor = CreativeActor.user(command.userId());
        LocalDateTime now = LocalDateTime.now();
        if (run.getStatus() != CreativeRunStatus.CANCEL_REQUESTED) {
            CreativeRunStateMachine.requireTransition(run.getStatus(), CreativeRunStatus.CANCEL_REQUESTED);
            run.setStatus(CreativeRunStatus.CANCEL_REQUESTED);
            run.setCancelRequestedAt(now);
            run.setActiveConversationId(null);
            run.setUpdatedBy(actor);
            runRepository.update(run);
            eventService.append(run.getRunId(), CreativeEventType.RUN_CANCEL_REQUESTED, null);
            run = runRepository.lockOwned(command.runId(), command.userId())
                    .orElseThrow(() -> new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN));
        }
        CreativeRunStateMachine.requireTransition(run.getStatus(), CreativeRunStatus.CANCELLED);
        run.setStatus(CreativeRunStatus.CANCELLED);
        run.setFinishedAt(now);
        run.setLeaseUntil(null);
        run.setUpdatedBy(actor);
        runRepository.update(run);
        eventService.append(run.getRunId(), CreativeEventType.RUN_CANCELLED, null);
        log.info("Creative Run cancelled runId={} userId={}", run.getRunId(), command.userId());
    }

    /** 复用已有会话，或为新的会话触发请求创建会话。 */
    private CreativeConversation resolveConversation(StartCreativeRunCommand command) {
        if (command.triggerSource() != CreativeRunTriggerSource.CONVERSATION) {
            return null;
        }
        if (StringUtils.hasText(command.conversationId())) {
            CreativeConversation conversation = conversationRepository
                    .findOwned(command.conversationId(), command.userId())
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
                .createdBy(actor)
                .updatedBy(actor)
                .build());
    }

    /** 首次显式绑定项目时更新会话，拒绝把已有会话切换到其他项目。 */
    private void bindProjectIfNecessary(CreativeConversation conversation, Long requestedProjectId, long userId) {
        if (Objects.isNull(requestedProjectId)) {
            return;
        }
        if (Objects.nonNull(conversation.getProjectId())
                && !requestedProjectId.equals(conversation.getProjectId())) {
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
        if (Objects.nonNull(command.projectId())) {
            return command.projectId();
        }
        return Objects.isNull(conversation) ? null : conversation.getProjectId();
    }

    /** 构造只包含受理阶段信息的 QUEUED Run。 */
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
            throw new IllegalStateException(
                    CreativeRuntimeMessages.REQUEST_FINGERPRINT_SERIALIZATION_FAILED, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(CreativeRuntimeMessages.SHA_256_UNAVAILABLE, exception);
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
                ? normalized
                : normalized.substring(0, CONVERSATION_TITLE_MAX_LENGTH);
    }
}
