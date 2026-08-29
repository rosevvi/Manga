package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.common.enums.UserRole;
import com.manga.entity.Role;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 定义角色目录和用户角色关联的数据访问契约。
 */
public interface RoleMapper extends BaseMapper<Role> {

    List<Role> findAllRoles();

    int insertIfAbsent(
            @Param("roleCode") UserRole roleCode,
            @Param("roleName") String roleName,
            @Param("roleDescription") String roleDescription,
            @Param("actor") String actor);

    int deleteUserRoles(@Param("userId") long userId);

    int insertUserRoles(
            @Param("userId") long userId,
            @Param("roles") Set<UserRole> roles,
            @Param("actor") String actor);
}
