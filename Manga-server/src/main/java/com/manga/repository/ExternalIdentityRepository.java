package com.manga.repository;

import com.manga.common.enums.ExternalIdentityProvider;
import com.manga.entity.ExternalIdentity;
import com.manga.mapper.ExternalIdentityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 使用 MyBatis-Plus 维护平台用户与外部身份提供方之间的绑定。
 */
@Repository
@RequiredArgsConstructor
public class ExternalIdentityRepository {

    private final ExternalIdentityMapper externalIdentityMapper;

    /**
     * 按提供方和外部用户标识查找已绑定的平台用户。
     */
    public Optional<Long> findUserId(ExternalIdentityProvider provider, String providerUserId) {
        return Optional.ofNullable(externalIdentityMapper.findUserId(provider, providerUserId));
    }

    /**
     * 创建外部身份与平台用户的唯一绑定。
     */
    public void create(
            long userId,
            ExternalIdentityProvider provider,
            String providerUserId,
            String providerUnionId,
            String actor) {
        externalIdentityMapper.insert(ExternalIdentity.builder()
                .userId(userId)
                .provider(provider)
                .providerUserId(providerUserId)
                .providerUnionId(providerUnionId)
                .createdBy(actor)
                .updatedBy(actor)
                .build());
    }
}
