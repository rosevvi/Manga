package com.manga.agent.model;

import com.manga.agent.blueprint.ModelSnapshot;
import com.manga.common.enums.AiProviderType;
import com.manga.entity.AiProviderConfig;

import java.util.Set;

/**
 * 将平台 AI 配置适配为特定 AgentScope 模型实现。
 */
public interface AgentChatModelProvider {

    /** 返回当前适配器支持的服务商或协议类型。 */
    Set<AiProviderType> supportedTypes();

    /** 创建由 Kernel 负责关闭的模型实例。 */
    OwnedChatModel create(
            AiProviderConfig config,
            ModelSnapshot snapshot,
            String apiKey,
            String proxyPassword);
}
