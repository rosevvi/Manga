package com.manga.repository;

import com.manga.common.enums.UserRole;
import com.manga.entity.Role;
import com.manga.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * 使用 MyBatis-Plus 维护角色目录和用户角色关联。
 */
@Repository
@RequiredArgsConstructor
public class RoleRepository {

    private final RoleMapper roleMapper;

    /**
     * 查询系统中的全部角色。
     */
    public List<Role> findAll() {
        return roleMapper.findAllRoles();
    }

    /**
     * 确保系统枚举中定义的角色已经写入数据库。
     */
    public void ensureExists(UserRole role, String actor) {
        roleMapper.insertIfAbsent(role, role.displayName(), role.description(), actor);
    }

    /**
     * 使用给定角色集合替换用户当前角色。
     */
    public void replaceUserRoles(long userId, Set<UserRole> roles, String actor) {
        roleMapper.deleteUserRoles(userId);
        if (!roles.isEmpty()) {
            roleMapper.insertUserRoles(userId, roles, actor);
        }
    }
}
