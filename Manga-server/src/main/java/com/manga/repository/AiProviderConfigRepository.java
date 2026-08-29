package com.manga.repository;

import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.entity.AiProviderConfig;
import com.manga.mapper.AiProviderConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 使用 MyBatis-Plus 持久化用户 AI 服务配置。 */
@Repository
@RequiredArgsConstructor
public class AiProviderConfigRepository {

    private final AiProviderConfigMapper aiProviderConfigMapper;

    public List<AiProviderConfig> findAllOwnedBy(long ownerUserId) {
        return aiProviderConfigMapper.findAllOwnedBy(ownerUserId);
    }

    public Optional<AiProviderConfig> findOwnedById(long configId, long ownerUserId) {
        return Optional.ofNullable(aiProviderConfigMapper.findOwnedById(configId, ownerUserId));
    }

    public Optional<AiProviderConfig> findDefaultOwnedBy(long ownerUserId) {
        return Optional.ofNullable(aiProviderConfigMapper.findDefaultOwnedBy(ownerUserId));
    }

    public boolean existsOwnedByName(long ownerUserId, String name, Long excludedConfigId) {
        return aiProviderConfigMapper.countOwnedByName(ownerUserId, name, excludedConfigId) > 0;
    }

    public AiProviderConfig create(AiProviderConfig config) {
        aiProviderConfigMapper.insert(config);
        if (config.getId() == null) {
            throw new IllegalStateException(ExceptionMessageConstants.GENERATED_AI_PROVIDER_CONFIG_ID_UNAVAILABLE);
        }
        return config;
    }

    public void update(AiProviderConfig config, long ownerUserId) {
        aiProviderConfigMapper.updateOwned(config, ownerUserId);
    }

    public void delete(long configId) {
        aiProviderConfigMapper.deleteById(configId);
    }

    public void clearDefault(long ownerUserId, String actor) {
        aiProviderConfigMapper.clearDefault(ownerUserId, actor);
    }

    public void setDefault(long configId, long ownerUserId, String actor) {
        aiProviderConfigMapper.setDefault(configId, ownerUserId, actor);
    }
}
