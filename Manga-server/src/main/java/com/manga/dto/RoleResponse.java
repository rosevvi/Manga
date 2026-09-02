package com.manga.dto;

import java.time.LocalDateTime;

/**
 * 返回角色目录中的角色定义。
 */
public record RoleResponse(
        /** 角色主键。 */
        Long id,
        /** 稳定业务编码。 */
        String code,
        /** 角色名称。 */
        String name,
        /** 角色描述。 */
        String description,
        /** 角色是否允许管理员分配。 */
        boolean assignable,
        /** 创建时间。 */
        LocalDateTime createdAt,
        /** 最后更新时间。 */
        LocalDateTime updatedAt,
        /** 创建人标识。 */
        String createdBy,
        /** 最后更新人标识。 */
        String updatedBy
) {
}
