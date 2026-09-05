package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 保存剧本场景中的对白或旁白。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_project_script_dialogue")
public class ProjectScriptDialogue {

    /** 对白主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属场景主键。 */
    private Long sceneId;
    /** 说话角色。 */
    private String speaker;
    /** 对白或旁白文本。 */
    private String text;
    /** 对白类型。 */
    private String dialogueType;
    /** 对白排序。 */
    private Integer sortOrder;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
