package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.UserAccount;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 定义平台用户及其角色聚合的数据访问契约。
 */
public interface UserMapper extends BaseMapper<UserAccount> {

    /** 按用户名查询账号及角色。 */
    UserAccount findByUsername(@Param("username") String username);

    /** 按 ID 查询账号及角色。 */
    UserAccount findById(@Param("userId") long userId);

    /** 查询全部账号及角色。 */
    List<UserAccount> findAllWithRoles();

    /** 更新用户显示名称。 */
    int updateDisplayName(
            @Param("userId") long userId,
            @Param("displayName") String displayName,
            @Param("actor") String actor);

    /** 更新用户密码哈希。 */
    int updatePasswordHash(
            @Param("userId") long userId,
            @Param("passwordHash") String passwordHash,
            @Param("actor") String actor);
}
