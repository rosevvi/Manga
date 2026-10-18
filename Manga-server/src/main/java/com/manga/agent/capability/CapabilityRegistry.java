package com.manga.agent.capability;

import com.manga.agent.domain.CreativeRuntimeMessages;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 按稳定名称维护精确的 Manga Agent Capability 白名单。
 */
@Component
public class CapabilityRegistry {

    private final Map<CapabilityName, MangaAgentCapability> capabilities;

    /** 校验并建立唯一能力索引。 */
    public CapabilityRegistry(List<MangaAgentCapability> candidates) {
        EnumMap<CapabilityName, MangaAgentCapability> indexed = new EnumMap<>(CapabilityName.class);
        for (MangaAgentCapability candidate : candidates) {
            CapabilityName name = candidate.descriptor().name();
            if (Objects.nonNull(indexed.put(name, candidate))) {
                throw new IllegalStateException(
                        CreativeRuntimeMessages.CAPABILITY_DUPLICATED_TEMPLATE.formatted(name.code()));
            }
        }
        capabilities = Map.copyOf(indexed);
    }

    /** 返回指定能力，不存在时立即失败。 */
    public MangaAgentCapability require(CapabilityName name) {
        MangaAgentCapability capability = capabilities.get(name);
        if (Objects.isNull(capability)) {
            throw new IllegalArgumentException(
                    CreativeRuntimeMessages.CAPABILITY_NOT_FOUND_TEMPLATE.formatted(name.code()));
        }
        return capability;
    }

    /** 返回指定白名单对应的稳定排序能力清单。 */
    public List<CapabilityDescriptor> descriptors(Set<CapabilityName> whitelist) {
        return whitelist.stream()
                .sorted(Comparator.comparing(CapabilityName::code))
                .map(this::require)
                .map(MangaAgentCapability::descriptor)
                .toList();
    }
}
