package com.manga.agent.generation;

import com.manga.agent.application.CreativeEventService;
import com.manga.agent.application.CreativeIdentifierGenerator;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.changeset.ChangeSetConstants;
import com.manga.agent.changeset.CreativeRunStopped;
import com.manga.agent.config.CreativeRuntimeProperties;
import com.manga.agent.domain.CreativeActor;
import com.manga.agent.domain.CreativeDispatchOutboxStatus;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.event.CreativeEventSources;
import com.manga.agent.execution.CreativeDispatchSignal;
import com.manga.agent.execution.CreativeRuntimeInstanceIdentity;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeArtifact;
import com.manga.entity.CreativeChangeSet;
import com.manga.entity.CreativeDispatchOutbox;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.entity.GenerationJob;
import com.manga.entity.GenerationMediaObject;
import com.manga.repository.GenerationMediaObjectRepository;
import com.manga.service.storage.GeneratedMediaReference;
import com.manga.repository.CreativeDispatchOutboxRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import com.manga.repository.GenerationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/** Run→Job→Capacity 固定锁顺序；事务只推进状态，不执行网络或文件 IO。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationJobService {
    private final GenerationJobRepository jobs;
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final CreativeDispatchOutboxRepository dispatch;
    private final CreativeEventService events;
    private final AgentStateSessionService sessions;
    private final CreativeIdentifierGenerator identifiers;
    private final CreativeRuntimeInstanceIdentity identity;
    private final CreativeRuntimeProperties runtime;
    private final GenerationProperties properties;
    private final GenerationProviderRegistry providers;
    private final ImageGenerationQuoteService quotes;
    private final ChangeSetCodec codec;
    private final ApplicationEventPublisher publisher;
    private final GenerationMediaObjectRepository media;
    private final GenerationCleanupProperties cleanupProperties;

    /** 批准事务内创建单次提交意图；价格或端点变化要求重新生成预览。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enqueue(CreativeChangeSet proposal, long userId, String actor) {
        ImageGenerationChange input = codec.read(proposal.getPayloadJson(), ImageGenerationChange.class);
        try {
            if (!input.equals(quotes.canonicalize(input, userId))) return false;
        } catch (BusinessException exception) {
            // 禁用、删除或更换端点均要求重新预览，不能沿用旧的费用批准。
            return false;
        }
        String config = input.provider() == GenerationProviderKey.FAKE ? null : codec.write(input.configSnapshot());
        jobs.create(GenerationJob.builder().jobId(UUID.randomUUID().toString()).runId(proposal.getRunId())
                .stepId(proposal.getStepId()).changeSetId(proposal.getChangeSetId()).userId(userId)
                .projectId(proposal.getProjectId()).shotId(input.shotId()).provider(input.provider()).configId(input.configId())
                .configSnapshotJson(config).requestJson(codec.write(input)).status(GenerationJobStatus.PENDING_SUBMISSION)
                .ownerEpoch(0L).cancelRequested(false).attempts(0).nextActionAt(LocalDateTime.now())
                .createdBy(actor).updatedBy(actor).build());
        return true;
    }

    /** 审批结果与 WAITING_JOB 一起提交；已退休的旧 StateSession 不重新启用。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void await(CreativeChangeSet proposal, String result, String actor) {
        if (!steps.awaitJob(proposal.getStepId(), result, actor)) throw new BusinessException(GenerationError.STATE);
        GenerationJob job = jobs.byStep(proposal.getStepId()).orElseThrow();
        publisher.publishEvent(new GenerationJobSignal(job.getJobId()));
    }

    /** 领取到期作业；非幂等提交租约过期时进入 UNKNOWN，绝不重复付费 POST。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GenerationJob claim(String id) {
        GenerationJob candidate = jobs.find(id).orElse(null);
        if (Objects.isNull(candidate)) return null;
        CreativeRun run = runs.lockByRunId(candidate.getRunId());
        GenerationJob job = jobs.lock(id);
        LocalDateTime now = LocalDateTime.now();
        if (job.getStatus().isTerminal() || job.getStatus() == GenerationJobStatus.UNKNOWN
                || job.getNextActionAt().isAfter(now)
                || Objects.nonNull(job.getLeaseUntil()) && job.getLeaseUntil().isAfter(now)) return null;
        long previous = job.getOwnerEpoch();
        if (run.getStatus().isTerminal() || run.getStatus() == CreativeRunStatus.CANCEL_REQUESTED) job.setCancelRequested(true);
        if (job.getStatus() == GenerationJobStatus.SUBMITTING && !providers.require(job.getProvider()).idempotentSubmission()) {
            unknown(job, GenerationError.SUBMISSION_UNKNOWN); jobs.save(job, previous); emit(job, CreativeEventType.JOB_PROGRESS); return null;
        }
        if (job.getStatus() == GenerationJobStatus.PENDING_SUBMISSION) {
            if (Boolean.TRUE.equals(job.getCancelRequested())) {
                terminal(job, GenerationJobStatus.CANCELLED, null); jobs.save(job, previous);
                jobs.submission(id, SubmissionStatus.DONE, actor()); finish(job); return null;
            }
            if (!jobs.capacity(job, properties.providerConcurrency(), properties.userConcurrency())) {
                job.setNextActionAt(now.plus(properties.pollInterval())); jobs.save(job, previous); return null;
            }
            transition(job, GenerationJobStatus.SUBMITTING); job.setSubmittedAt(now);
            jobs.submission(id, SubmissionStatus.CLAIMED, actor());
            emit(job, CreativeEventType.JOB_SUBMITTED);
        } else if (Objects.nonNull(job.getSubmittedAt()) && job.getSubmittedAt().plus(properties.maxAge()).isBefore(now)) {
            if (job.getStatus() == GenerationJobStatus.MATERIALIZING) {
                terminal(job, GenerationJobStatus.FAILED, GenerationError.STORAGE); jobs.save(job, previous); finish(job); return null;
            }
            unknown(job, GenerationError.TIMEOUT); jobs.save(job, previous); emit(job, CreativeEventType.JOB_PROGRESS); return null;
        }
        job.setOwnerEpoch(previous + 1); job.setOwnerInstanceId(identity.value()); job.setLeaseUntil(now.plus(properties.lease()));
        job.setAttempts(job.getAttempts() + 1); job.setUpdatedBy(actor()); jobs.save(job, previous);
        return job;
    }

    /** 已获确定响应后才保存结果；新取消标记以数据库为准，旧 worker 不可复活作业。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void accept(GenerationJob claim, GenerationSubmission result) {
        runs.lockByRunId(claim.getRunId());
        GenerationJob job = jobs.lock(claim.getJobId());
        if (!owned(job, claim)) return;
        GenerationSnapshot snapshot = result.snapshot();
        if (job.getStatus() == GenerationJobStatus.SUBMITTING) jobs.submission(job.getJobId(), SubmissionStatus.DONE, actor());
        job.setProviderJobId(result.providerJobId()); job.setProgress(snapshot.progress());
        job.setErrorCode(Objects.isNull(snapshot.error()) ? null : snapshot.error().code());
        job.setErrorMessage(Objects.isNull(snapshot.error()) ? null : snapshot.error().message());
        if (Boolean.TRUE.equals(job.getCancelRequested()) && snapshot.status() == GenerationJobStatus.MATERIALIZING) {
            terminal(job, GenerationJobStatus.CANCELLED, null);
        } else if (snapshot.status().isTerminal()) {
            terminal(job, snapshot.status(), snapshot.error());
        } else {
            transition(job, snapshot.status()); job.setResultBase64(snapshot.imageBase64());
        }
        release(job); jobs.save(job, claim.getOwnerEpoch());
        if (job.getStatus().isTerminal()) finish(job); else {
            emit(job, CreativeEventType.JOB_PROGRESS);
            if (job.getStatus() == GenerationJobStatus.MATERIALIZING) publisher.publishEvent(new GenerationJobSignal(job.getJobId()));
        }
    }

    /** 已转存文件在此原子发布成唯一 Artifact，取消或失去租约时禁止发布。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void materialized(GenerationJob claim, long mediaObjectId, long bytes, String digest) {
        CreativeRun run = runs.lockByRunId(claim.getRunId());
        GenerationJob job = jobs.lock(claim.getJobId());
        if (!owned(job, claim)) return;
        GenerationMediaObject object = media.lock(mediaObjectId);
        if (Objects.isNull(object) || !Objects.equals(object.getJobId(), job.getJobId())
                || !Objects.equals(object.getOwnerEpoch(), claim.getOwnerEpoch()) || object.getStatus() != GenerationMediaStatus.PENDING) {
            throw new GenerationException(GenerationError.STATE);
        }
        if (Boolean.TRUE.equals(job.getCancelRequested()) || run.getStatus().isTerminal()
                || run.getStatus() == CreativeRunStatus.CANCEL_REQUESTED) terminal(job, GenerationJobStatus.CANCELLED, null);
        else {
            jobs.createArtifact(CreativeArtifact.builder().jobId(job.getJobId()).runId(job.getRunId()).stepId(job.getStepId())
                    .changeSetId(job.getChangeSetId()).userId(job.getUserId()).projectId(job.getProjectId()).shotId(job.getShotId())
                    .url(object.getUrl()).mediaObjectId(mediaObjectId).mediaType("image/png").byteSize(bytes).sha256(digest).createdBy(actor()).updatedBy(actor()).build());
            media.publish(mediaObjectId, actor());
            terminal(job, GenerationJobStatus.SUCCEEDED, null);
        }
        jobs.save(job, claim.getOwnerEpoch());
        if (job.getStatus() == GenerationJobStatus.SUCCEEDED) emit(job, CreativeEventType.ARTIFACT_CREATED);
        finish(job);
    }

    /** 文件 IO 前登记确定的物理位置；失去执行权时不得产生新的写入对象。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GenerationMediaObject prepareMedia(GenerationJob claim, GeneratedMediaReference reference) {
        runs.lockByRunId(claim.getRunId()); GenerationJob job = jobs.lock(claim.getJobId());
        if (!owned(job, claim) || job.getStatus() != GenerationJobStatus.MATERIALIZING) throw new GenerationException(GenerationError.STATE);
        return media.create(GenerationMediaObject.builder().jobId(job.getJobId()).ownerEpoch(job.getOwnerEpoch())
                .storageType(reference.storageType()).storageNamespace(reference.namespace()).objectName(reference.name()).url(reference.url())
                .status(GenerationMediaStatus.PENDING).attempts(0).nextAttemptAt(LocalDateTime.now().plus(cleanupProperties.grace()))
                .createdBy(actor()).updatedBy(actor()).build());
    }

    /** 提交失败按确定性分类；查询/转存故障只退避当前阶段，不回到付费提交。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void failed(GenerationJob claim, GenerationError error) {
        runs.lockByRunId(claim.getRunId());
        GenerationJob job = jobs.lock(claim.getJobId());
        if (!owned(job, claim)) return;
        if (job.getStatus() == GenerationJobStatus.SUBMITTING) {
            if (error == GenerationError.AUTHENTICATION || error == GenerationError.REJECTED || error == GenerationError.RATE_LIMIT) {
                terminal(job, GenerationJobStatus.FAILED, error); jobs.submission(job.getJobId(), SubmissionStatus.DONE, actor());
            } else unknown(job, GenerationError.SUBMISSION_UNKNOWN);
        } else if (job.getStatus() == GenerationJobStatus.MATERIALIZING && error == GenerationError.PROTOCOL) {
            terminal(job, GenerationJobStatus.FAILED, error);
        } else {
            job.setErrorCode(error.code()); job.setErrorMessage(error.message()); release(job);
            long multiplier = 1L << Math.min(job.getAttempts(), 10);
            job.setNextActionAt(LocalDateTime.now().plus(Duration.ofMillis(Math.min(properties.maxBackoff().toMillis(), properties.pollInterval().toMillis() * multiplier))));
        }
        jobs.save(job, claim.getOwnerEpoch());
        if (job.getStatus().isTerminal()) finish(job); else emit(job, CreativeEventType.JOB_PROGRESS);
        log.warn("Generation action failed jobId={} phase={} error={}", job.getJobId(), job.getStatus(), error.code());
    }

    /** 与 Run 取消同事务撤销采用权；有效 worker 保留原租约以等待真实响应。 */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void stopped(CreativeRunStopped stopped) {
        for (String id : jobs.byRun(stopped.runId())) {
            GenerationJob job = jobs.lock(id); job.setCancelRequested(true); job.setUpdatedBy(actor());
            jobs.save(job, job.getOwnerEpoch());
        }
    }

    /** 用户核对供应商后仅结束平台等待；不重提，也不声称供应商停止或退款。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void acknowledgeUnknown(String id, long userId, String note) {
        GenerationJob candidate = jobs.find(id).filter(job -> job.getUserId() == userId)
                .orElseThrow(() -> new BusinessException(GenerationError.NOT_FOUND));
        runs.lockByRunId(candidate.getRunId()); GenerationJob job = jobs.lock(id);
        if (Objects.nonNull(job.getReconciledAt())) return;
        if (job.getStatus() != GenerationJobStatus.UNKNOWN) throw new BusinessException(GenerationError.STATE);
        job.setReconciledBy(userId); job.setReconciledAt(LocalDateTime.now()); job.setReconciliationNote(note.trim());
        terminal(job, GenerationJobStatus.FAILED, GenerationError.SUBMISSION_UNKNOWN);
        job.setUpdatedBy(CreativeActor.user(userId)); jobs.save(job, job.getOwnerEpoch());
        jobs.submission(id, SubmissionStatus.DONE, job.getUpdatedBy()); finish(job);
    }

    /** 在 Run 锁下撤销采用权；正在执行的 worker 继续收集事实，不能绕过未知提交核对。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(String id, long userId) {
        GenerationJob candidate = jobs.find(id).filter(job -> job.getUserId() == userId)
                .orElseThrow(() -> new BusinessException(GenerationError.NOT_FOUND));
        runs.lockByRunId(candidate.getRunId()); GenerationJob job = jobs.lock(id);
        if (job.getStatus().isTerminal() || Boolean.TRUE.equals(job.getCancelRequested())) return;
        job.setCancelRequested(true); job.setUpdatedBy(CreativeActor.user(userId));
        if (Objects.isNull(job.getLeaseUntil())) job.setNextActionAt(LocalDateTime.now());
        jobs.save(job, job.getOwnerEpoch()); emit(job, CreativeEventType.JOB_PROGRESS);
        publisher.publishEvent(new GenerationJobSignal(id));
    }

    /** 旧代次、过期 lease 和终态均不具备结果写权限。 */
    private boolean owned(GenerationJob job, GenerationJob claim) {
        return !job.getStatus().isTerminal() && job.getStatus() != GenerationJobStatus.UNKNOWN
                && Objects.equals(job.getOwnerInstanceId(), claim.getOwnerInstanceId())
                && Objects.equals(job.getOwnerEpoch(), claim.getOwnerEpoch())
                && Objects.nonNull(job.getLeaseUntil()) && job.getLeaseUntil().isAfter(LocalDateTime.now());
    }
    /** 清除执行权，并按阶段安排下一次读取；转存可立即执行。 */
    private void release(GenerationJob job) {
        job.setOwnerInstanceId(null); job.setLeaseUntil(null); job.setUpdatedBy(actor());
        job.setNextActionAt(job.getStatus() == GenerationJobStatus.MATERIALIZING ? LocalDateTime.now() : LocalDateTime.now().plus(properties.pollInterval()));
    }
    /** 未知请求保留风险容量和人工核对责任。 */
    private void unknown(GenerationJob job, GenerationError error) {
        transition(job, GenerationJobStatus.UNKNOWN); job.setErrorCode(error.code()); job.setErrorMessage(error.message()); release(job);
        jobs.submission(job.getJobId(), SubmissionStatus.UNKNOWN, actor());
    }
    /** 终态撤销执行权，清除暂存媒体，避免长期重复保留大对象。 */
    private void terminal(GenerationJob job, GenerationJobStatus status, GenerationError error) {
        transition(job, status); job.setFinishedAt(LocalDateTime.now()); job.setResultBase64(null);
        job.setErrorCode(Objects.isNull(error) ? null : error.code()); job.setErrorMessage(Objects.isNull(error) ? null : error.message()); release(job);
        if (status == GenerationJobStatus.SUCCEEDED) job.setProgress(100);
    }
    /** 事件和恢复命令携带同一安全摘要。 */
    private GenerationJobEvent summary(GenerationJob job) {
        CreativeArtifact artifact = jobs.artifact(job.getJobId()).orElse(null);
        return new GenerationJobEvent(job.getJobId(), job.getStatus(), job.getProgress(), Objects.isNull(artifact) ? null : artifact.getId(),
                Objects.isNull(artifact) ? null : artifact.getUrl(), job.getErrorCode(), job.getErrorMessage());
    }
    /** 在 Run 锁内追加可重放事件；事件服务维护 sequence 和投影水位。 */
    private void emit(GenerationJob job, CreativeEventType type) {
        events.append(job.getRunId(), job.getStepId(), type, CreativeEventSources.RUNTIME, job.getJobId(), summary(job));
    }
    /** 终态只恢复仍在 WAITING_JOB 的原步骤；Run 取消后不复活、不创建新会话状态。 */
    private void finish(GenerationJob job) {
        emit(job, job.getStatus() == GenerationJobStatus.SUCCEEDED ? CreativeEventType.JOB_COMPLETED
                : job.getStatus() == GenerationJobStatus.CANCELLED ? CreativeEventType.JOB_CANCELLED : CreativeEventType.JOB_FAILED);
        CreativeRun run = runs.lockByRunId(job.getRunId());
        if (run.getStatus().isTerminal() || run.getStatus() == CreativeRunStatus.CANCEL_REQUESTED) return;
        String sessionId = identifiers.nextStateSessionId();
        if (!steps.resumeJob(job.getStepId(), sessionId, codec.write(summary(job)), ChangeSetConstants.RESUME_MAX_ATTEMPTS, actor())) return;
        run.setDeadlineAt(LocalDateTime.now().plus(runtime.runTimeout())); run.setUpdatedBy(actor()); runs.update(run);
        sessions.register(run, sessionId);
        CreativeStep step = steps.findByStepId(job.getStepId()).orElseThrow();
        dispatch.create(CreativeDispatchOutbox.builder().stepId(step.getStepId()).scheduleVersion(step.getScheduleVersion())
                .status(CreativeDispatchOutboxStatus.PENDING).attempts(0).createdBy(actor()).updatedBy(actor()).build());
        publisher.publishEvent(new CreativeDispatchSignal(step.getStepId(), step.getScheduleVersion()));
    }
    /** 系统异步操作的审计身份。 */
    private String actor() { return SystemActor.CREATIVE_RUNTIME.code(); }
    /** 所有状态写入共用有限状态机，不能绕过未知提交的人工核对分支。 */
    private void transition(GenerationJob job, GenerationJobStatus target) {
        GenerationJobStateMachine.requireTransition(job.getStatus(), target); job.setStatus(target);
    }
}
