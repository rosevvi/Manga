package com.manga.agent.operations;

import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.collaboration.CollaborationGuard;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.entity.ModelCallLedger;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import com.manga.repository.RuntimeOperationsRepository;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.ModelHttpException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.time.Duration;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

/** 请求发出前持久化受理；关闭时以 callId CAS 幂等结算，失联调用绝不自动重发。 */
@Service
@RequiredArgsConstructor
public class ModelUsageService {
    private final RuntimeOperationsRepository repository;
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final RuntimeOperationsProperties properties;
    private final MeterRegistry metrics;

    /** Run → 配置门禁按固定顺序加锁，限制跨实例并发、分钟速率和每日规划预留。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ModelCallLedger begin(AgentBlueprint blueprint, AgentRuntimeContext context) {
        if (!context.deadline().isAfter(Instant.now()))
            throw new AgentRuntimeException(CreativeErrorCode.DEADLINE_EXCEEDED, RuntimeOperationsConstants.CALL_EXPIRED);
        var run = runs.lockByRunId(context.runId());
        CollaborationGuard.requireRun(run, steps.findByStepId(context.stepId()).orElse(null), context);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String actor = SystemActor.CREATIVE_RUNTIME.code();
        var gate = repository.lockGate(blueprint.model().configId(), actor);
        boolean probe = gate.getConsecutiveFailures() >= properties.failureThreshold();
        if ((Objects.nonNull(gate.getOpenUntil()) && gate.getOpenUntil().isAfter(now))
                || (probe && repository.active(gate.getConfigId(), now) > 0)
                || repository.active(gate.getConfigId(), now) >= properties.maxConcurrent()
                || repository.reserved(gate.getConfigId(), now.toLocalDate().atStartOfDay())
                    .add(properties.reservedUsdPerCall()).compareTo(properties.dailyReservedUsd()) > 0) reject();
        LocalDateTime minute = now.truncatedTo(ChronoUnit.MINUTES);
        if (!gate.getWindowStartedAt().equals(minute)) {
            gate.setWindowStartedAt(minute); gate.setWindowRequests(0);
        }
        if (gate.getWindowRequests() >= properties.requestsPerMinute()) reject();
        gate.setWindowRequests(gate.getWindowRequests() + 1);
        if (probe) gate.setOpenUntil(now.plus(properties.circuitCooldown()));
        repository.saveGate(gate, actor);
        var call = new ModelCallLedger();
        call.setCallId(UUID.randomUUID().toString()); call.setRunId(context.runId()); call.setStepId(context.stepId());
        call.setOwnerEpoch(context.ownerEpoch()); call.setConfigId(blueprint.model().configId());
        call.setModelName(blueprint.model().modelName()); call.setBlueprintFingerprint(blueprint.fingerprint());
        call.setStatus(ModelCallStatus.STARTED); call.setUsageReported(false);
        call.setReservedUsd(properties.reservedUsdPerCall());
        call.setStartedAt(now);
        Instant timeout = Instant.now().plus(properties.callTimeout());
        call.setExpiresAt(LocalDateTime.ofInstant(timeout.isBefore(context.deadline()) ? timeout : context.deadline(), ZoneOffset.UTC));
        var price = properties.prices().get(call.getModelName());
        if (Objects.nonNull(price)) {
            call.setInputPrice(price.input()); call.setCachedInputPrice(price.cachedInput()); call.setOutputPrice(price.output());
        }
        call.setCreatedBy(actor); call.setUpdatedBy(actor); repository.create(call);
        return call;
    }

    /** 完成、异常、取消均关闭账本；缺失 usage 或未定价时 calculatedUsd 保持空。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void finish(ModelCallLedger call, ModelCallStatus status, Throwable failure) {
        var gate = repository.lockGate(call.getConfigId(), SystemActor.CREATIVE_RUNTIME.code());
        // 取消可与最后一块 usage 并发；在同一锁内封口，避免读取到只更新了一半的计数。
        synchronized (call) {
            call.setStatus(status); call.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
            if (Objects.nonNull(failure)) call.setFailureType(failure.getClass().getSimpleName());
            if (Boolean.TRUE.equals(call.getUsageReported()) && Objects.nonNull(call.getInputPrice())
                    && Objects.requireNonNullElse(call.getCacheCreationTokens(), 0L) == 0
                    && Objects.requireNonNullElse(call.getToolUsePromptTokens(), 0L) == 0) {
                long cached = Math.min(call.getInputTokens(), call.getCachedTokens());
                call.setCalculatedUsd(call.getInputPrice().multiply(BigDecimal.valueOf(call.getInputTokens() - cached))
                        .add(call.getCachedInputPrice().multiply(BigDecimal.valueOf(cached)))
                        .add(call.getOutputPrice().multiply(BigDecimal.valueOf(call.getOutputTokens())))
                        .divide(BigDecimal.valueOf(1000000), 8, RoundingMode.HALF_UP));
            }
        }
        if (!repository.finish(call, SystemActor.CREATIVE_RUNTIME.code())) return;
        if (status == ModelCallStatus.SUCCEEDED) {
            gate.setConsecutiveFailures(0); gate.setOpenUntil(null);
        } else if (status == ModelCallStatus.UNKNOWN || retryable(failure)) {
            gate.setConsecutiveFailures(gate.getConsecutiveFailures() + 1);
            if (gate.getConsecutiveFailures() >= properties.failureThreshold())
                gate.setOpenUntil(call.getFinishedAt().plus(properties.circuitCooldown()));
        }
        repository.saveGate(gate, SystemActor.CREATIVE_RUNTIME.code());
        // 标签仅使用有限状态，不把 runId、用户或任意模型名变成高基数标签。
        metrics.counter(RuntimeOperationsConstants.CALLS_METRIC, "status", status.name()).increment();
        metrics.timer(RuntimeOperationsConstants.LATENCY_METRIC, "status", status.name())
                .record(Duration.between(call.getStartedAt(), call.getFinishedAt()));
    }

    /** SDK 返回累计 usage；取各计数最大值，避免流式尾块重复和累计值被逐块相加。 */
    public static void observe(ModelCallLedger call, ChatUsage usage) {
        if (Objects.isNull(usage)) return;
        synchronized (call) {
            if (call.getStatus() != ModelCallStatus.STARTED) return;
            call.setUsageReported(true);
            call.setInputTokens(max(call.getInputTokens(), usage.getInputTokens()));
            call.setOutputTokens(max(call.getOutputTokens(), usage.getOutputTokens()));
            call.setCachedTokens(max(call.getCachedTokens(), usage.getCachedTokens()));
            call.setReasoningTokens(max(call.getReasoningTokens(), usage.getReasoningTokens()));
            call.setCacheCreationTokens(max(call.getCacheCreationTokens(), usage.getCacheCreationTokens()));
            call.setToolUsePromptTokens(max(call.getToolUsePromptTokens(), usage.getToolUsePromptTokens()));
        }
    }

    /** 查明异常链的 HTTP 状态，不解析或记录可能带凭据的异常消息。 */
    private boolean retryable(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; Objects.nonNull(current) && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof ModelHttpException http && Objects.nonNull(http.getStatusCode()))
                return http.getStatusCode() == 429 || http.getStatusCode() >= 500;
            if (current instanceof IOException || current instanceof TimeoutException) return true;
        }
        return false;
    }
    /** 无 usage 时保持未知，已知计数不接受供应商负值。 */
    private static long max(Long current, int value) { return Math.max(Objects.requireNonNullElse(current, 0L), Math.max(0, value)); }
    /** 拒绝保护边界，不自动切换模型或重试付费请求。 */
    private void reject() {
        metrics.counter(RuntimeOperationsConstants.REJECTIONS_METRIC).increment();
        throw new AgentRuntimeException(CreativeErrorCode.MODEL_INVOCATION_FAILED, RuntimeOperationsConstants.REJECTED);
    }
}
