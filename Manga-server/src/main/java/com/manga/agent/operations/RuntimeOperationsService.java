package com.manga.agent.operations;

import com.manga.agent.application.CreativeRunQueryService;
import com.manga.repository.RuntimeOperationsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.math.BigDecimal;
import com.manga.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 运维与用户账本查询边界；任何普通用户查询先检查 Run 所有权。 */
@Service
@RequiredArgsConstructor
public class RuntimeOperationsService {
    private final RuntimeOperationsRepository repository;
    private final CreativeRunQueryService runs;

    /** 普通用户只可按游标查询自己 Run 的调用记录。 */
    @Transactional(readOnly = true)
    public List<RuntimeOperationsViews.Call> ownedUsage(String runId, long userId, Long beforeId) {
        runs.findOwned(runId, userId); return repository.byRun(runId, beforeId).stream().map(RuntimeOperationsViews.Call::from).toList();
    }

    /** 管理员的有界诊断快照，未包含模型连接、提示词或密钥。 */
    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        var gates = repository.gates();
        var now = LocalDateTime.now(ZoneOffset.UTC);
        return new Snapshot(repository.overview(), repository.backlog(), gates.stream().map(RuntimeOperationsViews.Gate::from).toList(),
                gates.stream().filter(g -> Objects.nonNull(g.getOpenUntil()) && g.getOpenUntil().isAfter(now)).count());
    }

    /** 管理员按 runId 追踪全部分页用量，不隐式返回业务正文。 */
    @Transactional(readOnly = true)
    public List<RuntimeOperationsViews.Call> adminUsage(String runId, Long beforeId) {
        return repository.byRun(runId, beforeId).stream().map(RuntimeOperationsViews.Call::from).toList();
    }

    /** 账单确认只执行一次；重复相同金额与依据幂等，不允许覆盖原始对账记录。 */
    @Transactional
    public void settle(String callId, long userId, Settlement request) {
        var call = repository.lockCall(callId);
        if (Objects.isNull(call) || call.getStatus() == ModelCallStatus.STARTED)
            throw new BusinessException(RuntimeOperationsError.SETTLEMENT_CONFLICT, HttpStatus.CONFLICT);
        if (Objects.nonNull(call.getSettledUsd())) {
            if (call.getSettledUsd().compareTo(request.amountUsd()) != 0 || !Objects.equals(call.getSettlementNote(), request.note()))
                throw new BusinessException(RuntimeOperationsError.SETTLEMENT_CONFLICT, HttpStatus.CONFLICT);
            return;
        }
        if (!repository.settle(callId, request.amountUsd(), userId, request.note(), LocalDateTime.now(ZoneOffset.UTC)))
            throw new BusinessException(RuntimeOperationsError.SETTLEMENT_CONFLICT, HttpStatus.CONFLICT);
    }

    /** 管理员对账输入，金额为零也须明确提供账单依据。 */
    public record Settlement(
            /** 供应商核对后的 USD。 */ @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 8) BigDecimal amountUsd,
            /** 账单编号或对账依据，禁止包含凭据。 */ @NotBlank @Size(max = 500) String note
    ) { }

    /** 监测快照；费用聚合包含部分用量，须结合 unpriced_or_unknown_calls 解读。 */
    public record Snapshot(
            /** 近 24 小时调用状态、token 和可计算费用。 */ List<Map<String,Object>> usage,
            /** Run 状态分布及最老记录时间。 */ List<Map<String,Object>> backlog,
            /** 最近 100 个模型配置门禁。 */ List<RuntimeOperationsViews.Gate> providerGates,
            /** 上述有界门禁中仍在冷却的数量。 */ long openCircuits
    ) { }
}
