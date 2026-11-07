package com.manga.mapper;

import com.manga.entity.AgentProfileRecord;
import com.manga.entity.AgentProfileVersion;
import com.manga.entity.AgentProfileAudit;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** Agent 配置的有界查询、聚合锁与内置身份幂等初始化。 */
public interface AgentProfileQueryMapper {
    /** 按稳定标识锁定发布指针。 */
    AgentProfileRecord lock(@Param("key") String key);
    /** 查询配置元数据。 */
    AgentProfileRecord find(@Param("key") String key);
    /** 管理员有界目录。 */
    List<AgentProfileRecord> profiles(@Param("limit") int limit);
    /** 读取精确版本。 */
    AgentProfileVersion version(@Param("key") String key, @Param("version") long version);
    /** 在调用方已有一致性快照时仍读取发布指针对应的最新版本。 */
    AgentProfileVersion lockVersion(@Param("key") String key, @Param("version") long version);
    /** 有界读取版本摘要所需记录。 */
    List<AgentProfileVersion> versions(@Param("key") String key, @Param("limit") int limit);
    /** 批量查询启用且已发布的定义，目录读取不产生 N+1。 */
    List<AgentProfileVersion> activeVersions(@Param("limit") int limit);
    /** 审计游标分页。 */
    List<AgentProfileAudit> audits(@Param("key") String key, @Param("beforeId") Long beforeId, @Param("limit") int limit);
    /** 统计配置数，在目录锚点锁内检查创建上限。 */
    int countProfiles();
    /** 只登记缺失内置身份，不覆盖管理员编辑和发布指针。 */
    int seed(@Param("key") String key, @Param("actor") String actor);
}
