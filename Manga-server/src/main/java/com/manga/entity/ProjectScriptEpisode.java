package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 保存剧本分集。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_project_script_episode")
public class ProjectScriptEpisode {

    /** 分集主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属剧本主键。 */
    private Long scriptId;
    /** 分集编号。 */
    private Integer episodeNumber;
    /** 分集标题。 */
    private String title;
    /** 分集简介。 */
    private String summary;
    /** 分集排序。 */
    private Integer sortOrder;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
