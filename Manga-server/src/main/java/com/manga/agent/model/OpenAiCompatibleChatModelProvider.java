package com.manga.agent.model;

import com.manga.agent.blueprint.ModelSnapshot;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.common.enums.AiProviderType;
import com.manga.common.enums.AiProxyType;
import com.manga.entity.AiProviderConfig;
import io.agentscope.core.model.transport.ProxyConfig;
import io.agentscope.core.model.transport.ProxyType;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * 创建 OpenAI Chat Completions 兼容协议的 AgentScope 流式模型。
 */
@Component
public class OpenAiCompatibleChatModelProvider implements AgentChatModelProvider {

    private static final Set<AiProviderType> SUPPORTED_TYPES = Set.of(
            AiProviderType.OPENAI,
            AiProviderType.OPENAI_COMPATIBLE,
            AiProviderType.DEEPSEEK,
            AiProviderType.DASHSCOPE,
            AiProviderType.VOLCENGINE,
            AiProviderType.NEWAPI,
            AiProviderType.CUSTOM
    );

    @Override
    public Set<AiProviderType> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    /** 创建启用流式输出和原生工具结构化输出的模型。 */
    @Override
    public OwnedChatModel create(
            AiProviderConfig config,
            ModelSnapshot snapshot,
            String apiKey,
            String proxyPassword) {
        if (config.getProviderType().apiKeyRequired()
                && (Objects.isNull(apiKey) || apiKey.isBlank())) {
            throw new IllegalStateException(CreativeRuntimeMessages.AGENT_MODEL_API_KEY_REQUIRED);
        }
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(Objects.isNull(apiKey) ? "" : apiKey)
                .modelName(snapshot.modelName())
                .baseUrl(snapshot.baseUrl())
                .contextWindowSize(snapshot.contextWindow())
                .nativeStructuredOutputWithTools(true)
                .stream(true);
        ProxyConfig proxy = proxy(config, proxyPassword);
        if (Objects.nonNull(proxy)) {
            builder.proxy(proxy);
        }
        return new OwnedChatModel(builder.build());
    }

    /** 将平台代理设置转换为 AgentScope 网络代理。 */
    private ProxyConfig proxy(AiProviderConfig config, String proxyPassword) {
        AiProxyType proxyType = AiProxyType.normalize(config.getProxyType());
        if (!proxyType.enabled()) {
            return null;
        }
        ProxyConfig.Builder builder = ProxyConfig.builder()
                .type(proxyType == AiProxyType.HTTP ? ProxyType.HTTP : ProxyType.SOCKS5)
                .host(config.getProxyHost())
                .port(config.getProxyPort());
        if (Objects.nonNull(config.getProxyUsername()) && !config.getProxyUsername().isBlank()) {
            builder.username(config.getProxyUsername()).password(proxyPassword);
        }
        return builder.build();
    }
}
