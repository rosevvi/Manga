package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.TaskStatus;
import com.manga.common.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 持久化可恢复的业务后台任务。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_task")
public class MangaTask {

    /** 任务主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 任务所有者。 */
    private Long ownerUserId;
    /** 所属项目。 */
    private Long projectId;
    /** 任务类型。 */
    private TaskType taskType;
    /** 任务状态。 */
    private TaskStatus status;
    /** 任务标题。 */
    private String title;
    /** 原始输入临时文件引用。 */
    private String sourceReference;
    /** 原始输入摘要哈希。 */
    private String sourceSha256;
    /** 原始输入字节数。 */
    private Long sourceSize;
    /** 原始输入类型。 */
    private String sourceContentType;
    /** 插入位置之前的章节主键。 */
    private Long afterChapterId;
    /** 总处理单元数。 */
    private Integer totalUnits;
    /** 已完成单元数。 */
    private Integer completedUnits;
    /** 失败单元数。 */
    private Integer failedUnits;
    /** 当前处理单元标题。 */
    private String currentUnit;
    /** 最近一次错误。 */
    private String lastError;
    /** 重试次数。 */
    private Integer attemptCount;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 开始时间。 */
    private LocalDateTime startedAt;
    /** 完成时间。 */
    private LocalDateTime finishedAt;
    /** 创建人标识。 */
    private String createdBy;
    /** 更新人标识。 */
    private String updatedBy;
}
