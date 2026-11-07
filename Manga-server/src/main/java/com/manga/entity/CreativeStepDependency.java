package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.collaboration.JoinPolicy;
import lombok.Data;
import java.time.LocalDateTime;

/** 持久化协作步骤依赖；所有变更在所属 Run 行锁内完成。 */
@Data
@TableName("manga_creative_step_dependency")
public class CreativeStepDependency {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属运行。 */
    private String runId;
    /** 必须先成功的步骤。 */
    private String upstreamStepId;
    /** 等待前置结果的步骤。 */
    private String downstreamStepId;
    /** 首版固定 ALL_SUCCESS。 */
    private JoinPolicy joinPolicy;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 最后更新操作者。 */
    private String updatedBy;
}

