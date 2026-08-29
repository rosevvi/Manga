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

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer sortOrder;
    private String shotNumber;
    private String title;
    private String sceneName;
    private String shotType;
    private String cameraMovement;
    private Integer durationSeconds;
    private String content;
    private String dialogue;
    private String soundEffect;
    private String imageUrl;
    private String notes;
    private StoryboardShotStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
