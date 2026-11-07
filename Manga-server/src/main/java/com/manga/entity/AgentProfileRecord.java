package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;

import lombok.Data;
import java.time.LocalDateTime;

/** 管理员 Agent 配置的聚合发布指针。 */
@Data
@TableName("manga_agent_profile")
public class AgentProfileRecord {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 管理员定义的稳定标识。 */
    private String profileKey;
    /** 是否为平台初始化的内置定义。 */
    private Boolean builtIn;
    /** 是否允许新的运行选择该配置。 */
    private Boolean enabled;
    /** 当前发布版本，未发布时为空。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long activeVersion;
    /** 已分配的最大版本号。 */
    private Long lastVersion;
    /** 管理员操作的乐观并发版本。 */
    private Long revision;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 最后更新操作者。 */
    private String updatedBy;
}

