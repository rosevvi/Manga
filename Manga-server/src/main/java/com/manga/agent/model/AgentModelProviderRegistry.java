package com.manga.agent.model;

import com.manga.agent.blueprint.ModelSnapshot;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.common.enums.AiProviderType;
import com.manga.entity.AiProviderConfig;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 按 AI 服务协议路由唯一的 AgentScope 模型适配器。
 */
@Component
public class AgentModelProviderRegistry {

    private final Map<AiProviderType, AgentChatModelProvider> providers;

    /** 校验并建立服务商到模型适配器的唯一映射。 */
    public AgentModelProviderRegistry(List<AgentChatModelProvider> candidates) {
        EnumMap<AiProviderType, AgentChatModelProvider> indexed = new EnumMap<>(AiProviderType.class);
        for (AgentChatModelProvider candidate : candidates) {
            for (AiProviderType type : candidate.supportedTypes()) {
                if (Objects.nonNull(indexed.put(type, candidate))) {
                    throw new IllegalStateException(
                            CreativeRuntimeMessages.AGENT_MODEL_PROVIDER_DUPLICATED_TEMPLATE.formatted(type));
                }
            }
        }
        providers = Map.copyOf(indexed);
    }

    /** 使用与配置协议匹配的唯一适配器创建模型。 */
    public OwnedChatModel create(
            AiProviderConfig config,
            ModelSnapshot snapshot,
            String apiKey,
            String proxyPassword) {
        AgentChatModelProvider provider = providers.get(config.getProviderType());
        if (Objects.isNull(provider)) {
            throw new IllegalStateException(
                    CreativeRuntimeMessages.AGENT_MODEL_PROVIDER_UNSUPPORTED_TEMPLATE
                            .formatted(config.getProviderType()));
        }
        return provider.create(config, snapshot, apiKey, proxyPassword);
    }
}
