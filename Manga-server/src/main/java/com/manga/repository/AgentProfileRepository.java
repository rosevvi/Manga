package com.manga.repository;

import com.manga.entity.AgentProfileRecord;
import com.manga.entity.AgentProfileVersion;
import com.manga.entity.AgentProfileAudit;
import com.manga.mapper.AgentProfileRecordMapper;
import com.manga.mapper.AgentProfileVersionMapper;
import com.manga.mapper.AgentProfileAuditMapper;
import com.manga.mapper.AgentProfileQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;

/** 配置聚合持久化；版本发布和状态判断由管理服务承担。 */
@Repository
@RequiredArgsConstructor
public class AgentProfileRepository {
    private final AgentProfileRecordMapper profiles;
    private final AgentProfileVersionMapper versions;
    private final AgentProfileAuditMapper audits;
    private final AgentProfileQueryMapper queries;

    /** 锁住身份及发布指针，串行化编辑和新运行选择。 */
    public AgentProfileRecord lock(String key) { return queries.lock(key); }
    /** 查询身份元数据。 */
    public AgentProfileRecord find(String key) { return queries.find(key); }
    /** 查询有界配置目录。 */
    public List<AgentProfileRecord> profiles(int limit) { return queries.profiles(limit); }
    /** 查询精确内容版本。 */
    public AgentProfileVersion version(String key, long version) { return queries.version(key, version); }
    /** 身份锁内读取精确发布版本，避免可重复读事务中的旧草稿快照。 */
    public AgentProfileVersion lockVersion(String key, long version) { return queries.lockVersion(key, version); }
    /** 查询有界版本记录。 */
    public List<AgentProfileVersion> versions(String key, int limit) { return queries.versions(key, limit); }
    /** 批量查询发布目录。 */
    public List<AgentProfileVersion> activeVersions(int limit) { return queries.activeVersions(limit); }
    /** 查询审计下一页。 */
    public List<AgentProfileAudit> audits(String key, Long beforeId, int limit) { return queries.audits(key, beforeId, limit); }
    /** 统计目录大小。 */
    public int countProfiles() { return queries.countProfiles(); }
    /** 初始化缺失内置身份。 */
    public void seed(String key, String actor) { queries.seed(key, actor); }
    /** 创建专业 Agent 身份。 */
    public void create(AgentProfileRecord value) { profiles.insert(value); }
    /** 创建新的版本。 */
    public void create(AgentProfileVersion value) { versions.insert(value); }
    /** 追加审计，不提供更新或删除入口。 */
    public void append(AgentProfileAudit value) { audits.insert(value); }
    /** 更新已持有行锁且通过 revision 校验的身份。 */
    public void update(AgentProfileRecord value) { profiles.updateById(value); }
    /** 仅由管理服务在草稿或首次发布时更新。 */
    public void update(AgentProfileVersion value) { versions.updateById(value); }
}
