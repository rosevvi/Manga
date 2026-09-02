package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.ProjectMemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 表示项目协作成员及其在项目内的角色。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_project_member")
public class ProjectMember {

    /** 项目成员主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属项目主键。 */
    private Long projectId;
    /** 关联用户主键。 */
    private Long userId;
    /** 项目成员角色。 */
    private ProjectMemberRole role;
    /** 用户名。 */
    @TableField(exist = false)
    private String username;
    /** 用户显示名称。 */
    @TableField(exist = false)
    private String displayName;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建人标识。 */
    private String createdBy;
    /** 最后更新人标识。 */
    private String updatedBy;
}
