package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 保存剧本场景。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_project_script_scene")
public class ProjectScriptScene {

    /** 场景主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属分集主键。 */
    private Long episodeId;
    /** 场景编号。 */
    private Integer sceneNumber;
    /** 场景地点。 */
    private String location;
    /** 场景时间描述。 */
    private String timeDescription;
    /** 场景摘要。 */
    private String summary;
    /** 场景画面内容。 */
    private String content;
    /** 场景排序。 */
    private Integer sortOrder;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
