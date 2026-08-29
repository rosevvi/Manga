package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.UserAccount;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 定义平台用户及其角色聚合的数据访问契约。
 */
public interface UserMapper extends BaseMapper<UserAccount> {

    UserAccount findByUsername(@Param("username") String username);

    UserAccount findById(@Param("userId") long userId);

    List<UserAccount> findAllWithRoles();

    int updateDisplayName(
            @Param("userId") long userId,
            @Param("displayName") String displayName,
            @Param("actor") String actor);

    int updatePasswordHash(
            @Param("userId") long userId,
            @Param("passwordHash") String passwordHash,
            @Param("actor") String actor);
}
