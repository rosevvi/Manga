package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 表示可授予平台用户的角色记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_role")
public class Role {

    /** 角色主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 稳定业务编码。 */
    private UserRole code;
    /** 角色名称。 */
    private String name;
    /** 角色描述。 */
    private String description;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建人标识。 */
    private String createdBy;
    /** 最后更新人标识。 */
    private String updatedBy;
}
