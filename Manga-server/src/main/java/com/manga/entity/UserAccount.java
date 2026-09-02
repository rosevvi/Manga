package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.UserRole;
import com.manga.common.enums.UserStatus;
import com.manga.common.enums.RegistrationSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * 表示数据库中的平台用户及其角色集合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_user")
public class UserAccount {

    /** 用户账号主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户名。 */
    private String username;
    /** BCrypt 密码摘要。 */
    @ToString.Exclude
    private String passwordHash;
    /** 用户显示名称。 */
    private String displayName;
    /** 用户账号状态。 */
    private UserStatus status;
    /** 账号注册来源。 */
    private RegistrationSource registrationSource;
    /** 当前用户角色集合。 */
    @TableField(exist = false)
    @Builder.Default
    private Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建人标识。 */
    private String createdBy;
    /** 最后更新人标识。 */
    private String updatedBy;

    /**
     * 将联表聚合出的角色编码转换为领域枚举集合。
     */
    public void setRoleCodes(String roleCodes) {
        EnumSet<UserRole> parsedRoles = EnumSet.noneOf(UserRole.class);
        if (roleCodes != null && !roleCodes.isBlank()) {
            for (String roleCode : roleCodes.split(",")) {
                parsedRoles.add(UserRole.valueOf(roleCode));
            }
        }
        this.roles = parsedRoles;
    }
}
