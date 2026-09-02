package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.StoryboardShotStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 表示项目时间线中的单个分镜镜头。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_storyboard_shot")
public class StoryboardShot {

    /** 分镜镜头主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属项目主键。 */
    private Long projectId;
    /** 分镜排序值。 */
    private Integer sortOrder;
    /** 分镜镜头编号。 */
    private String shotNumber;
    /** 分镜标题。 */
    private String title;
    /** 分镜场景名称。 */
    private String sceneName;
    /** 景别或镜头类型。 */
    private String shotType;
    /** 镜头运动方式。 */
    private String cameraMovement;
    /** 镜头时长，单位为秒。 */
    private Integer durationSeconds;
    /** 分镜画面内容。 */
    private String content;
    /** 分镜对白。 */
    private String dialogue;
    /** 分镜音效说明。 */
    private String soundEffect;
    /** 分镜参考图片地址。 */
    private String imageUrl;
    /** 分镜制作备注。 */
    private String notes;
    /** 分镜镜头状态。 */
    private StoryboardShotStatus status;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建人标识。 */
    private String createdBy;
    /** 最后更新人标识。 */
    private String updatedBy;
}
