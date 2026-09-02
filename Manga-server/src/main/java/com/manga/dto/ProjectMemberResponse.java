package com.manga.dto;

import com.manga.common.enums.ProjectMemberRole;

import java.time.LocalDateTime;

/** 返回项目协作成员的公开摘要。 */
public record ProjectMemberResponse(
        /** 项目成员主键。 */
        Long id,
        /** 所属项目主键。 */
        Long projectId,
        /** 关联用户主键。 */
        Long userId,
        /** 用户名。 */
        String username,
        /** 用户显示名称。 */
        String displayName,
        /** 项目成员角色。 */
        ProjectMemberRole role,
        /** 创建时间。 */
        LocalDateTime createdAt,
        /** 最后更新时间。 */
        LocalDateTime updatedAt
) {
}
