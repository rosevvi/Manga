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

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    @ToString.Exclude
    private String passwordHash;
    private String displayName;
    private UserStatus status;
    private RegistrationSource registrationSource;
    @TableField(exist = false)
    @Builder.Default
    private Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
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
