package com.manga.repository;

import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.common.enums.RegistrationSource;
import com.manga.common.enums.UserStatus;
import com.manga.entity.UserAccount;
import com.manga.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 使用 MyBatis-Plus 聚合平台用户基础信息和角色数据。
 */
@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final UserMapper userMapper;

    /**
     * 按用户名查询用户及其角色。
     */
    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(userMapper.findByUsername(username));
    }

    /**
     * 按主键查询用户及其角色。
     */
    public Optional<UserAccount> findById(long userId) {
        return Optional.ofNullable(userMapper.findById(userId));
    }

    /**
     * 一次联表查询全部用户及其角色，避免逐用户查询角色。
     */
    public List<UserAccount> findAll() {
        return userMapper.findAllWithRoles();
    }

    /**
     * 创建平台用户并返回数据库生成的主键。
     */
    public long create(
            String username,
            String passwordHash,
            String displayName,
            UserStatus status,
            RegistrationSource registrationSource,
            String actor) {
        UserAccount user = UserAccount.builder()
                .username(username)
                .passwordHash(passwordHash)
                .displayName(displayName)
                .status(status)
                .registrationSource(registrationSource)
                .createdBy(actor)
                .updatedBy(actor)
                .build();
        userMapper.insert(user);
        if (user.getId() == null) {
            throw new IllegalStateException(ExceptionMessageConstants.GENERATED_USER_ID_UNAVAILABLE);
        }
        return user.getId();
    }

    /**
     * 更新用户显示名称并记录修改人。
     */
    public void updateDisplayName(long userId, String displayName, String actor) {
        userMapper.updateDisplayName(userId, displayName, actor);
    }

    /**
     * 更新用户密码摘要并记录修改人。
     */
    public void updatePasswordHash(long userId, String passwordHash, String actor) {
        userMapper.updatePasswordHash(userId, passwordHash, actor);
    }
}
