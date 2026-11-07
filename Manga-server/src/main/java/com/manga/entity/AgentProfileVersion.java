package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.profile.AgentProfileVersionStatus;
import lombok.Data;
import java.time.LocalDateTime;

/** 管理员 Agent 配置的版本快照。 */
@Data
@TableName("manga_agent_profile_version")
public class AgentProfileVersion {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属配置标识。 */
    private String profileKey;
    /** 配置内递增版本号。 */
    private Long versionNo;
    /** DRAFT 可编辑，PUBLISHED 不可变。 */
    private AgentProfileVersionStatus status;
    /** 严格 schema 的纯数据配置，不含密钥。 */
    private String definitionJson;
    /** 规范化定义的 SHA256 指纹。 */
    private String definitionFingerprint;
    /** 首次发布的时间，草稿为空。 */
    private LocalDateTime publishedAt;
    /** 首次发布的操作者，草稿为空。 */
    private String publishedBy;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 最后更新操作者。 */
    private String updatedBy;
}

