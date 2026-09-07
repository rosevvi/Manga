package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 保存单个任务中的章节处理单元状态。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_task_unit")
public class MangaTaskUnit {

    /** 单元主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属任务。 */
    private Long taskId;
    /** 输入顺序。 */
    private Integer unitIndex;
    /** 已创建章节主键。 */
    private Long chapterId;
    /** 章节标题。 */
    private String chapterTitle;
    /** 单元状态。 */
    private TaskStatus status;
    /** 错误摘要。 */
    private String errorMessage;
    /** 开始时间。 */
    private LocalDateTime startedAt;
    /** 完成时间。 */
    private LocalDateTime finishedAt;
}
