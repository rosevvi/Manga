package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.profile.AgentProfileAction;
import lombok.Data;
import java.time.LocalDateTime;

/** 管理员 Agent 配置的不可变审计记录。 */
@Data
@TableName("manga_agent_profile_audit")
public class AgentProfileAudit {
    /** 数据库主键及审计分页游标。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属配置标识。 */
    private String profileKey;
    /** 本次操作的相关版本。 */
    private Long versionNo;
    /** 发布与管理操作类型。 */
    private AgentProfileAction action;
    /** 操作前发布指针。 */
    private Long previousActiveVersion;
    /** 操作后发布指针。 */
    private Long activeVersion;
    /** 操作提交后的聚合版本。 */
    private Long profileRevision;
    /** 相关定义指纹，不重复存储 Prompt 正文。 */
    private String definitionFingerprint;
    /** 管理员操作原因，不保存 Prompt 正文。 */
    private String comment;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 最后更新操作者。 */
    private String updatedBy;
}

