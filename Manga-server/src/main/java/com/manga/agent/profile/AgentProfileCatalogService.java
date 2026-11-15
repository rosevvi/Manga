package com.manga.agent.profile;

import com.manga.agent.model.AgentModelProviderRegistry;
import com.manga.common.enums.AiModelType;
import com.manga.repository.AgentProfileRepository;
import com.manga.repository.AiProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

/** 用户与 Director 的发布目录，以及用户可选的对话模型。 */
@Service
@RequiredArgsConstructor
public class AgentProfileCatalogService {
    private final AgentProfileRepository profiles;
    private final AgentProfileDefinitionCodec codec;
    private final AiProviderConfigRepository configs;
    private final AgentModelProviderRegistry models;

    /** 返回用户自有且可用的文本配置，省略端点与密钥。 */
    @Transactional(readOnly = true)
    public List<ModelOption> modelOptions(long userId) {
        var supported = models.supportedTypes();
        return configs.findAllOwnedBy(userId).stream()
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()) && config.getModelType() == AiModelType.TEXT)
                .filter(config -> supported.contains(config.getProviderType()))
                .filter(config -> Objects.nonNull(config.getDefaultModel()) && !config.getDefaultModel().isBlank())
                .filter(config -> !config.getProviderType().apiKeyRequired() || Objects.nonNull(config.getApiKeyCiphertext()))
                .map(config -> new ModelOption(config.getId(), config.getName(), config.getDefaultModel()))
                .toList();
    }

    /** 对话框所需的最小模型摘要。 */
    public record ModelOption(
            /** 配置主键。 */ Long configId,
            /** 用户配置名称。 */ String name,
            /** 提交给供应商的模型标识。 */ String model
    ) { }

    /** 一次批量查询返回启用的发布版本，不泄露 Prompt 或用户模型配置。 */
    @Transactional(readOnly = true)
    public List<AgentProfileApi.CatalogItem> list() {
        return profiles.activeVersions(AgentProfileContracts.CATALOG_LIMIT).stream().map(version -> {
            var definition = codec.read(version.getDefinitionJson());
            return new AgentProfileApi.CatalogItem(version.getProfileKey(), definition.role(), version.getVersionNo(),
                    definition.displayName(), definition.description(), definition.permissionPolicy(),
                    AgentProfilePolicySupport.rootSelectable(definition.profile(version.getProfileKey(), version.getVersionNo())));
        }).toList();
    }
}
