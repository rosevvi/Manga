package com.manga.repository;

import com.manga.mapper.RuntimeOperationsMapper;
import com.manga.entity.ModelCallLedger;
import com.manga.entity.ProviderCallGate;
import com.manga.entity.CreativeRunArchive;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** 封装运维持久化，业务判定留在 Application Service。 */
@Repository
@RequiredArgsConstructor
public class RuntimeOperationsRepository {
    private final RuntimeOperationsMapper mapper;
    /** 创建不存在的门禁并锁定。 */
    public ProviderCallGate lockGate(long configId, String actor) { mapper.ensureGate(configId, actor); return mapper.lockGate(configId); }
    /** 保存持锁门禁。 */
    public void saveGate(ProviderCallGate gate, String actor) { mapper.saveGate(gate, actor); }
    /** 查询未过期的跨进程并发数。 */
    public int active(long configId, LocalDateTime now) { return mapper.activeCalls(configId, now); }
    /** 查询 UTC 当日不退款的预留额。 */
    public BigDecimal reserved(long configId, LocalDateTime start) { return mapper.dailyReserved(configId, start); }
    /** 调用前写入 STARTED；失败时禁止供应商请求。 */
    public void create(ModelCallLedger call) { mapper.insert(call); }
    /** 只结算仍在 STARTED 状态的同一个调用。 */
    public boolean finish(ModelCallLedger call, String actor) { return mapper.finish(call, actor) == 1; }
    /** 对账前锁定调用，避免重复或相互冲突的账单确认。 */
    public ModelCallLedger lockCall(String callId) { return mapper.lockCall(callId); }
    /** 仅写入尚未对账的调用；原始供应商用量不变。 */
    public boolean settle(String callId, BigDecimal amount, long userId, String note, LocalDateTime now) {
        return mapper.settle(callId, amount, userId, note, now) == 1;
    }
    /** 有限批次查找失联调用。 */
    public List<ModelCallLedger> stale(LocalDateTime now) { return mapper.stale(now); }
    /** 按 ID 游标分页，单页最多 100。 */
    public List<ModelCallLedger> byRun(String runId, Long beforeId) { return mapper.byRun(runId, beforeId); }
    /** 已完成调用按状态聚合，不把空费用视为已结算。 */
    public List<Map<String,Object>> overview() { return mapper.overview(LocalDateTime.now(ZoneOffset.UTC).minusDays(1)); }
    /** 返回有界门禁摘要。 */
    public List<ProviderCallGate> gates() { return mapper.gates(); }
    /** 返回运行状态和积压规模。 */
    public List<Map<String,Object>> backlog() { return mapper.backlog(); }
    /** 查找可归档的冷 Run。 */
    public List<String> archiveCandidates(LocalDateTime before) { return mapper.archiveCandidates(before); }
    /** 读取不可变归档。 */
    public CreativeRunArchive archive(String runId) { return mapper.archive(runId); }
    /** 保存一次性归档，不覆盖原副本。 */
    public void insertArchive(CreativeRunArchive archive) { mapper.insertArchive(archive); }
    /** 有界读取归档所需依赖。 */
    public List<Map<String,Object>> steps(String runId, int limit) { return mapper.archiveSteps(runId, limit); }
    /** 有界读取原始事件，保留可重放 payload。 */
    public List<Map<String,Object>> events(String runId, int limit) { return mapper.archiveEvents(runId, limit); }
    /** 有界读取消息投影。 */
    public List<Map<String,Object>> messages(String runId, int limit) { return mapper.archiveMessages(runId, limit); }
    /** 有界读取冻结协作计划。 */
    public List<Map<String,Object>> plans(String runId, int limit) { return mapper.archivePlans(runId, limit); }
    /** 有界读取候选变更。 */
    public List<Map<String,Object>> changes(String runId, int limit) { return mapper.archiveChanges(runId, limit); }
    /** 有界读取审批轨迹。 */
    public List<Map<String,Object>> approvals(String runId, int limit) { return mapper.archiveApprovals(runId, limit); }
    /** 仍活跃的媒体作业禁止归档。 */
    public int activeJobs(String runId) { return mapper.activeJobs(runId); }
    /** 先在数据库估算正文字符量，避免把超大文本载入 JVM 后才拒绝归档。 */
    public long archivePayloadSize(String runId) { return mapper.archivePayloadSize(runId); }
}
