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

    /** 监测快照；失败或取消的调用可能仅报告部分用量。 */
    public record Snapshot(
            /** 近 24 小时调用状态和已报告的 token。 */ List<Map<String,Object>> usage,
            /** Run 状态分布及最老记录时间。 */ List<Map<String,Object>> backlog,
            /** 最近 100 个模型配置门禁。 */ List<RuntimeOperationsViews.Gate> providerGates,
            /** 上述有界门禁中仍在冷却的数量。 */ long openCircuits
    ) { }
}
