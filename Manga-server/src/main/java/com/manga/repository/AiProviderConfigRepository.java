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

    /** 查询用户拥有的 AI 服务配置列表。 */
    public List<AiProviderConfig> findAllOwnedBy(long ownerUserId) {
        return aiProviderConfigMapper.findAllOwnedBy(ownerUserId);
    }

    /** 按 ID 查询用户拥有的 AI 服务配置。 */
    public Optional<AiProviderConfig> findOwnedById(long configId, long ownerUserId) {
        return Optional.ofNullable(aiProviderConfigMapper.findOwnedById(configId, ownerUserId));
    }

    /** 查询用户默认的 AI 服务配置。 */
    public Optional<AiProviderConfig> findDefaultOwnedBy(long ownerUserId) {
        return Optional.ofNullable(aiProviderConfigMapper.findDefaultOwnedBy(ownerUserId));
    }

    /** 判断用户是否存在同名 AI 服务配置。 */
    public boolean existsOwnedByName(long ownerUserId, String name, Long excludedConfigId) {
        return aiProviderConfigMapper.countOwnedByName(ownerUserId, name, excludedConfigId) > 0;
    }

    /** 创建 AI 服务配置。 */
    public AiProviderConfig create(AiProviderConfig config) {
        aiProviderConfigMapper.insert(config);
        if (config.getId() == null) {
            throw new IllegalStateException(ExceptionMessageConstants.GENERATED_AI_PROVIDER_CONFIG_ID_UNAVAILABLE);
        }
        return config;
    }

    /** 更新 AI 服务配置。 */
    public void update(AiProviderConfig config, long ownerUserId) {
        aiProviderConfigMapper.updateOwned(config, ownerUserId);
    }

    /** 删除 AI 服务配置。 */
    public void delete(long configId) {
        aiProviderConfigMapper.deleteById(configId);
    }

    /** 清除用户 AI 服务配置的默认标记。 */
    public void clearDefault(long ownerUserId, String actor) {
        aiProviderConfigMapper.clearDefault(ownerUserId, actor);
    }

    /** 设置用户默认的 AI 服务配置。 */
    public void setDefault(long configId, long ownerUserId, String actor) {
        aiProviderConfigMapper.setDefault(configId, ownerUserId, actor);
    }
}
