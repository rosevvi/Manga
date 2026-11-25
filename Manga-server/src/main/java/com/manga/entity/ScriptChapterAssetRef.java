package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 章节与项目资产的可复用关联。 */
@Data
@TableName("manga_script_chapter_asset_ref")
public class ScriptChapterAssetRef {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 引用资产的章节。 */
    private Long chapterId;
    /** 已加入章节的项目资产。 */
    private Long projectAssetId;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 更新操作者。 */
    private String updatedBy;
}
