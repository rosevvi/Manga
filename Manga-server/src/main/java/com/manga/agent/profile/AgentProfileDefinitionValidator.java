package com.manga.agent.profile;

import com.manga.agent.capability.CapabilityName;
import com.manga.agent.capability.CapabilityRegistry;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.model.AgentModelProviderRegistry;
import com.manga.common.enums.AiProviderType;
import com.manga.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Set;

/** 发布前检查可用能力、角色权限、模型适配器和预算，不执行任何模型或自定义代码。 */
@Component
@RequiredArgsConstructor
public class AgentProfileDefinitionValidator {
    private final CapabilityRegistry capabilities;
    private final AgentModelProviderRegistry models;
    private final AgentProfileDefinitionCodec codec;

    /** 返回可配置的模型协议，不连接外部供应商。 */
    public Set<AiProviderType> supportedProviders() { return models.supportedTypes(); }

    /** 定义按角色收敛权限；内置身份不得被改为其他角色族。 */
    public AgentProfileDefinition validate(String key, boolean builtIn, AgentProfileDefinition input) {
        AgentProfileDefinition definition = codec.normalize(input);
        if (!key.matches(AgentProfileContracts.KEY_PATTERN)) invalid();
        if (builtIn && definition.role() != AgentProfileKey.fromCode(key)) invalid();
        boolean director = definition.role() == AgentProfileKey.MANGA_DIRECTOR;
        if ((!director && definition.permissionPolicy() != AgentPermissionPolicy.READ_ONLY)
                || (director && !AgentPermissionPolicy.PROPOSE_CHANGES.allows(definition.permissionPolicy()))
                || definition.budget().maxReservedTurns() < definition.maxTurns() + 1) invalid();
        Set<CapabilityName> allowed = director ? CapabilityName.directorCapabilities() : CapabilityName.phaseTwoReadOnlyCapabilities();
        if (!allowed.containsAll(definition.capabilities())) invalid();
        for (var name : definition.capabilities()) {
            var descriptor = capabilities.require(name).descriptor();
            if (!definition.permissionPolicy().allows(descriptor.requiredPermission()) || !descriptor.inputSchema().isObject()) invalid();
        }
        for (var provider : definition.model().providers()) if (!models.supports(provider)) invalid();
        if (director && definition.model().selection() == AgentModelSelection.INHERIT_PARENT) invalid();
        return definition;
    }

    /** 返回统一可公开的配置校验失败。 */
    private void invalid() { throw new BusinessException(AgentProfileResponseCode.INVALID); }
}
