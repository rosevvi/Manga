package com.manga.agent.operations;

import com.manga.agent.capability.CapabilityContractSupport;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.domain.SystemActor;
import com.manga.entity.CreativeRunArchive;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.RuntimeOperationsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.manga.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 冷 Run 的只读审计归档；不删除在线事件、消息或被业务引用的产物。 */
@Service
@RequiredArgsConstructor
public class CreativeAuditArchiveService {
    private final RuntimeOperationsRepository repository;
    private final CreativeRunRepository runs;
    private final RuntimeOperationsProperties properties;
    private final ChangeSetCodec codec;

    /** 持有 Run 行锁生成幂等副本，审批/生成未收敛或消息投影未完成时拒绝。 */
    @Transactional
    public CreativeRunArchive archive(String runId, String actor) {
        var run = runs.lockByRunId(runId);
        var existing = repository.archive(runId);
        if (Objects.nonNull(existing)) return verify(existing);
        if (Objects.isNull(run) || !run.getStatus().isTerminal() || Objects.isNull(run.getTerminalSequence()) || repository.activeJobs(runId) > 0
                || (Objects.nonNull(run.getConversationId()) && run.getProjectedThroughSequence() < run.getTerminalSequence()))
            throw new BusinessException(RuntimeOperationsError.ARCHIVE_NOT_READY, HttpStatus.CONFLICT);
        // 为 JSON 转义和行元数据留余量；在读取 LONGTEXT 前阻止超大包进入 JVM。
        if (repository.archivePayloadSize(runId) > RuntimeOperationsConstants.MAX_ARCHIVE_CHARACTERS / 4)
            throw new BusinessException(RuntimeOperationsError.ARCHIVE_NOT_READY, HttpStatus.CONFLICT);
        int limit = properties.archiveMaxRows() + 1;
        var payload = new ArchivePayload(1, run, bounded(repository.steps(runId, limit)),
                bounded(repository.events(runId, limit)), bounded(repository.messages(runId, limit)),
                bounded(repository.plans(runId, limit)), bounded(repository.changes(runId, limit)),
                bounded(repository.approvals(runId, limit)));
        String json = codec.write(payload);
        if (json.length() > RuntimeOperationsConstants.MAX_ARCHIVE_CHARACTERS)
            throw new BusinessException(RuntimeOperationsError.ARCHIVE_NOT_READY, HttpStatus.CONFLICT);
        var result = new CreativeRunArchive();
        result.setRunId(runId); result.setSchemaVersion(1); result.setPayloadJson(json);
        result.setSha256(CapabilityContractSupport.sha256(json));
        result.setCreatedBy(actor); result.setUpdatedBy(actor);
        repository.insertArchive(result);
        return verify(repository.archive(runId));
    }

    /** 导出前验证格式、归属和摘要；返回源 JSON 字符串，避免重新序列化破坏校验。 */
    @Transactional(readOnly = true)
    public CreativeRunArchive export(String runId) {
        var archive = repository.archive(runId);
        if (Objects.isNull(archive)) throw new BusinessException(RuntimeOperationsError.ARCHIVE_NOT_READY, HttpStatus.NOT_FOUND);
        return verify(archive);
    }

    /** 离线恢复演练可调用同一校验器；只验证审计包，不覆盖现有生产业务数据。 */
    public CreativeRunArchive verify(CreativeRunArchive archive) {
        if (archive.getSchemaVersion() != 1 || !CapabilityContractSupport.sha256(archive.getPayloadJson()).equals(archive.getSha256()))
            throw new IllegalStateException(RuntimeOperationsConstants.ARCHIVE_MISMATCH);
        var restored = codec.read(archive.getPayloadJson(), ArchivePayload.class);
        if (restored.schemaVersion() != 1 || !archive.getRunId().equals(restored.run().getRunId()))
            throw new IllegalStateException(RuntimeOperationsConstants.ARCHIVE_MISMATCH);
        return archive;
    }

    /** 超限拒绝，禁止生成看似成功却缺少尾部数据的归档。 */
    private List<Map<String,Object>> bounded(List<Map<String,Object>> rows) {
        if (rows.size() > properties.archiveMaxRows()) throw new BusinessException(RuntimeOperationsError.ARCHIVE_NOT_READY, HttpStatus.CONFLICT);
        return rows;
    }

    /** 可校验、可解析的审计包；媒体对象需独立对象存储备份，用量账本单独长期保留。 */
    public record ArchivePayload(
            /** 审计包版本。 */ int schemaVersion,
            /** Run 元数据与冻结预算。 */ CreativeRun run,
            /** 步骤与 Blueprint。 */ List<Map<String,Object>> steps,
            /** 有序原始事件。 */ List<Map<String,Object>> events,
            /** 消息正文及工具状态。 */ List<Map<String,Object>> messages,
            /** 冻结委派配置。 */ List<Map<String,Object>> plans,
            /** 变更提案及结果。 */ List<Map<String,Object>> changes,
            /** 审批人和决定。 */ List<Map<String,Object>> approvals
    ) { }
}
