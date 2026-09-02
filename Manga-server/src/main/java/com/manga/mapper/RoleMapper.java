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

    /** 查询全部角色。 */
    List<Role> findAllRoles();

    /** 在角色不存在时写入角色。 */
    int insertIfAbsent(
            @Param("roleCode") UserRole roleCode,
            @Param("roleName") String roleName,
            @Param("roleDescription") String roleDescription,
            @Param("actor") String actor);

    /** 删除用户现有角色关系。 */
    int deleteUserRoles(@Param("userId") long userId);

    /** 批量写入用户角色关系。 */
    int insertUserRoles(
            @Param("userId") long userId,
            @Param("roles") Set<UserRole> roles,
            @Param("actor") String actor);
}
