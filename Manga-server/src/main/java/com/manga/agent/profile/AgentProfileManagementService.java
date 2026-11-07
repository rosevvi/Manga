package com.manga.agent.profile;

import com.manga.agent.capability.CapabilityName;
import com.manga.agent.capability.CapabilityRegistry;
import com.manga.agent.collaboration.CollaborationProperties;
import com.manga.agent.domain.CreativeActor;
import com.manga.agent.domain.SystemActor;
import com.manga.common.exception.BusinessException;
import com.manga.common.enums.AiProviderType;
import com.manga.entity.AgentProfileRecord;
import com.manga.entity.AgentProfileVersion;
import com.manga.entity.AgentProfileAudit;
import com.manga.repository.AgentProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 管理草稿、不可变发布版本和启停指针，所有变更都追加操作者审计。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentProfileManagementService {
    private final AgentProfileRepository profiles;
    private final AgentProfileDefinitionCodec codec;
    private final AgentProfileDefinitionValidator validator;
    private final AgentProfileManagementProperties properties;
    private final BuiltInAgentProfileSource builtIns;
    private final CollaborationProperties budgets;
    private final CapabilityRegistry capabilities;
    private final AgentProfileCatalogService catalog;

    /** 仅为缺失内置身份建立初始发布版，不覆盖管理员后续修改。幂等插入锁串行化多实例初始化。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void initializeBuiltIns() {
        String actor = SystemActor.CREATIVE_RUNTIME.code();
        for (AgentProfileKey key : AgentProfileKey.values()) {
            profiles.seed(key.code(), actor);
            AgentProfileRecord record = profiles.lock(key.code());
            if (record.getLastVersion() > 0) continue;
            AgentProfile builtIn = builtIns.require(key);
            AgentModelSelection selection = key == AgentProfileKey.MANGA_DIRECTOR || key == AgentProfileKey.SCRIPT_PARSER
                    ? AgentModelSelection.USER_SELECTABLE : AgentModelSelection.INHERIT_PARENT;
            AgentProfileDefinition definition = new AgentProfileDefinition(AgentProfileContracts.SCHEMA_VERSION, key,
                    builtIn.displayName(), builtIn.description(), builtIn.systemPrompt(), builtIn.allowedCapabilities(),
                    builtIn.permissionPolicy(), builtIn.maxTurns(), new AgentModelPolicy(selection, Set.of(), Set.of()),
                    new AgentBudgetLimits(budgets.maxSteps(), budgets.maxDepth(), budgets.maxParallel(), budgets.maxRepairRounds(),
                            budgets.maxInvocations(), budgets.maxReservedTurns()));
            definition = validator.validate(key.code(), true, definition);
            AgentProfileVersion version = newVersion(record, builtIn.version(), definition, actor);
            version.setStatus(AgentProfileVersionStatus.PUBLISHED);
            version.setPublishedAt(LocalDateTime.now()); version.setPublishedBy(actor);
            profiles.create(version);
            record.setLastVersion(version.getVersionNo()); record.setActiveVersion(version.getVersionNo());
            changed(record, actor);
            audit(record, version, AgentProfileAction.INITIALIZED, null, actor, null);
        }
    }

    /** 管理员有界身份目录。 */
    @Transactional(readOnly = true)
    public List<AgentProfileApi.Summary> list() {
        return profiles.profiles(AgentProfileContracts.CATALOG_LIMIT).stream().map(this::summary).toList();
    }

    /** 当前聚合指针和版本摘要，不批量返回历史 Prompt。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AgentProfileApi.Detail detail(String key) {
        var record = require(key, false);
        var versions = profiles.versions(key, AgentProfileContracts.CATALOG_LIMIT).stream().map(version ->
                new AgentProfileApi.VersionSummary(version.getVersionNo(), version.getStatus(), version.getDefinitionFingerprint(),
                        version.getPublishedAt(), version.getUpdatedBy())).toList();
        return new AgentProfileApi.Detail(summary(record), versions);
    }

    /** 读取精确版本及 schema 校验后的内容。 */
    @Transactional(readOnly = true)
    public AgentProfileApi.Version version(String key, long number) {
        require(key, false);
        var version = requireVersion(key, number);
        return new AgentProfileApi.Version(key, number, version.getStatus(), codec.read(version.getDefinitionJson()),
                version.getDefinitionFingerprint(), version.getPublishedAt(), version.getPublishedBy(), version.getUpdatedBy());
    }

    /** 创建身份及首个草稿，目录锚点锁使总数量检查在并发创建下仍然有效。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AgentProfileApi.Detail create(AgentProfileApi.Create request, long userId) {
        profiles.lock(AgentProfileKey.MANGA_DIRECTOR.code());
        if (profiles.countProfiles() >= properties.maxProfiles()) invalid();
        var definition = validator.validate(request.profileId(), false, codec.validate(request.definition()));
        String actor = CreativeActor.user(userId);
        AgentProfileRecord record = new AgentProfileRecord();
        record.setProfileKey(request.profileId()); record.setBuiltIn(false); record.setEnabled(true);
        record.setLastVersion(1L); record.setRevision(0L); record.setCreatedBy(actor); record.setUpdatedBy(actor);
        try { profiles.create(record); }
        catch (DuplicateKeyException duplicate) { throw new BusinessException(AgentProfileResponseCode.DUPLICATED, HttpStatus.CONFLICT); }
        var version = newVersion(record, 1, definition, actor); profiles.create(version);
        changed(record, actor); audit(record, version, AgentProfileAction.CREATED, null, actor, null);
        return detail(record.getProfileKey());
    }

    /** 新建草稿，不改变当前发布版本；每个身份的版本数量有界且不删除历史。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AgentProfileApi.Detail createDraft(String key, AgentProfileApi.Draft request, long userId) {
        var record = locked(key, request.expectedRevision());
        if (profiles.versions(key, AgentProfileContracts.CATALOG_LIMIT).size() >= properties.maxVersions()) invalid();
        var definition = validator.validate(key, record.getBuiltIn(), codec.validate(request.definition()));
        String actor = CreativeActor.user(userId);
        long number = record.getLastVersion() + 1;
        var version = newVersion(record, number, definition, actor); profiles.create(version);
        record.setLastVersion(number); changed(record, actor);
        audit(record, version, AgentProfileAction.DRAFT_CREATED, record.getActiveVersion(), actor, null);
        return detail(key);
    }

    /** 修改草稿必须携带 revision，发布内容永远不能原地编辑。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AgentProfileApi.Detail editDraft(String key, long number, AgentProfileApi.Draft request, long userId) {
        var record = locked(key, request.expectedRevision());
        var version = requireVersion(key, number);
        if (version.getStatus() != AgentProfileVersionStatus.DRAFT) throw new BusinessException(AgentProfileResponseCode.IMMUTABLE, HttpStatus.CONFLICT);
        var definition = validator.validate(key, record.getBuiltIn(), codec.validate(request.definition()));
        String actor = CreativeActor.user(userId);
        version.setDefinitionJson(codec.write(definition)); version.setDefinitionFingerprint(codec.fingerprint(definition));
        version.setUpdatedAt(LocalDateTime.now()); version.setUpdatedBy(actor); profiles.update(version);
        changed(record, actor); audit(record, version, AgentProfileAction.DRAFT_UPDATED, record.getActiveVersion(), actor, null);
        return detail(key);
    }

    /** 发布重新校验所有契约；指针、版本状态和审计同事务提交。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AgentProfileApi.Detail publish(String key, long number, AgentProfileApi.Release request, long userId) {
        return release(key, number, request, userId, false);
    }

    /** 回滚到已经发布的不可变内容，不复制或改写历史版本。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AgentProfileApi.Detail rollback(String key, long number, AgentProfileApi.Release request, long userId) {
        return release(key, number, request, userId, true);
    }

    /** 启停仅影响后续选择；已经冻结的 Run 和计划无需重新解析 Profile。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AgentProfileApi.Detail enable(String key, AgentProfileApi.Enable request, long userId) {
        var record = locked(key, request.expectedRevision());
        if (record.getEnabled().equals(request.enabled())) return detail(key);
        String actor = CreativeActor.user(userId); record.setEnabled(request.enabled()); changed(record, actor);
        audit(record, Objects.isNull(record.getActiveVersion()) ? null : requireVersion(key, record.getActiveVersion()),
                request.enabled() ? AgentProfileAction.ENABLED : AgentProfileAction.DISABLED, record.getActiveVersion(), actor, request.comment());
        return detail(key);
    }

    /** 管理员审计游标页，不提供删除或修改操作。 */
    @Transactional(readOnly = true)
    public List<AgentProfileApi.Audit> audits(String key, Long beforeId) {
        require(key, false);
        return profiles.audits(key, beforeId, AgentProfileContracts.AUDIT_PAGE_SIZE).stream().map(row ->
                new AgentProfileApi.Audit(row.getId(), row.getVersionNo(), row.getAction(), row.getPreviousActiveVersion(),
                        row.getActiveVersion(), row.getProfileRevision(), row.getComment(), row.getCreatedAt(), row.getCreatedBy())).toList();
    }

    /** 仅暴露可供管理员选择的现有业务能力，不允许配置平台内部结果提交工具。 */
    public List<AgentProfileApi.CapabilityOption> capabilities() {
        return capabilities.descriptors(CapabilityName.directorCapabilities()).stream().map(value ->
                new AgentProfileApi.CapabilityOption(value.name(), value.description(), value.requiredPermission(), value.schemaSha256())).toList();
    }

    /** 发布表单仅允许平台已经实现的对话模型协议。 */
    public Set<AiProviderType> modelProviders() { return validator.supportedProviders(); }

    /** 普通用户和 Director 读取当前发布目录，不缓存，发布和禁用后下次读取立即生效。 */
    @Transactional(readOnly = true)
    public List<AgentProfileApi.CatalogItem> catalog() {
        return catalog.list();
    }

    /** 统一发布与回滚的锁、校验和审计，禁止默认启用被管理员禁用的配置。 */
    private AgentProfileApi.Detail release(String key, long number, AgentProfileApi.Release request, long userId, boolean rollback) {
        var record = locked(key, request.expectedRevision()); var version = requireVersion(key, number);
        if (rollback && version.getStatus() != AgentProfileVersionStatus.PUBLISHED) invalid();
        var definition = validator.validate(key, record.getBuiltIn(), codec.read(version.getDefinitionJson()));
        if (!codec.fingerprint(definition).equals(version.getDefinitionFingerprint())) invalid();
        String actor = CreativeActor.user(userId); Long previous = record.getActiveVersion();
        if (version.getStatus() == AgentProfileVersionStatus.DRAFT) {
            version.setStatus(AgentProfileVersionStatus.PUBLISHED); version.setPublishedAt(LocalDateTime.now());
            version.setPublishedBy(actor); version.setUpdatedBy(actor); version.setUpdatedAt(LocalDateTime.now()); profiles.update(version);
        }
        record.setActiveVersion(number); changed(record, actor);
        audit(record, version, rollback ? AgentProfileAction.ROLLED_BACK : AgentProfileAction.PUBLISHED, previous, actor, request.comment());
        log.info("Agent Profile released profileId={} version={} revision={} rollback={} actor={}", key, number, record.getRevision(), rollback, actor);
        return detail(key);
    }

    /** 所有修改先锁身份并比较页面 revision，避免静默覆盖并发编辑。 */
    private AgentProfileRecord locked(String key, long expected) {
        var record = require(key, true);
        if (record.getRevision() != expected) throw new BusinessException(AgentProfileResponseCode.CONFLICT, HttpStatus.CONFLICT);
        return record;
    }

    /** 一致处理不存在的配置身份。 */
    private AgentProfileRecord require(String key, boolean lock) {
        var record = lock ? profiles.lock(key) : profiles.find(key);
        if (Objects.isNull(record)) throw new BusinessException(AgentProfileResponseCode.UNAVAILABLE, HttpStatus.NOT_FOUND);
        return record;
    }

    /** 一致处理不存在的版本，确保版本来自同一身份。 */
    private AgentProfileVersion requireVersion(String key, long number) {
        var version = profiles.version(key, number);
        if (Objects.isNull(version)) throw new BusinessException(AgentProfileResponseCode.UNAVAILABLE, HttpStatus.NOT_FOUND);
        return version;
    }

    /** 新草稿固定规范化 JSON 和指纹。 */
    private AgentProfileVersion newVersion(AgentProfileRecord record, long number, AgentProfileDefinition definition, String actor) {
        AgentProfileVersion version = new AgentProfileVersion(); version.setProfileKey(record.getProfileKey());
        version.setVersionNo(number); version.setStatus(AgentProfileVersionStatus.DRAFT); version.setDefinitionJson(codec.write(definition));
        version.setDefinitionFingerprint(codec.fingerprint(definition)); version.setCreatedBy(actor); version.setUpdatedBy(actor);
        return version;
    }

    /** 在身份锁内递增并发版本和操作者。 */
    private void changed(AgentProfileRecord record, String actor) {
        record.setRevision(record.getRevision() + 1); record.setUpdatedBy(actor); record.setUpdatedAt(LocalDateTime.now()); profiles.update(record);
    }

    /** 操作记录只保存指针、指纹和操作者，不记录完整 Prompt。 */
    private void audit(AgentProfileRecord record, AgentProfileVersion version, AgentProfileAction action, Long previous, String actor, String comment) {
        AgentProfileAudit audit = new AgentProfileAudit(); audit.setProfileKey(record.getProfileKey()); audit.setAction(action);
        audit.setVersionNo(Objects.isNull(version) ? null : version.getVersionNo()); audit.setPreviousActiveVersion(previous);
        audit.setActiveVersion(record.getActiveVersion()); audit.setProfileRevision(record.getRevision());
        audit.setDefinitionFingerprint(Objects.isNull(version) ? null : version.getDefinitionFingerprint()); audit.setComment(comment);
        audit.setCreatedBy(actor); audit.setUpdatedBy(actor); profiles.append(audit);
    }

    /** HTTP 元数据投影不泄露持久化实体。 */
    private AgentProfileApi.Summary summary(AgentProfileRecord row) {
        return new AgentProfileApi.Summary(row.getProfileKey(), row.getBuiltIn(), row.getEnabled(), row.getActiveVersion(),
                row.getLastVersion(), row.getRevision(), row.getUpdatedAt(), row.getUpdatedBy());
    }

    /** 发布协议、资源数量或模型配置不合法时拒绝提交。 */
    private void invalid() { throw new BusinessException(AgentProfileResponseCode.INVALID); }
}
