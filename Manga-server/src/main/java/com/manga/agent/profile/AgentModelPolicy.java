package com.manga.agent.profile;

import com.manga.common.enums.AiProviderType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.EnumSet;
import java.util.TreeSet;
import java.util.Collections;
import java.util.Objects;

/** 约束模型选择，不接受密钥、连接地址或其他用户的配置主键。 */
public record AgentModelPolicy(
        /** 根模型选择方式；Worker 始终继承父快照。 */ @NotNull AgentModelSelection selection,
        /** 允许的供应商，空集合表示平台支持的全部对话供应商。 */ @NotNull @Size(max = 16) Set<@NotNull AiProviderType> providers,
        /** 允许的模型名称，空集合表示不限制名称。 */ @NotNull @Size(max = 32) Set<@NotBlank @Size(max = 128) String> models
) {
    /** 固定集合顺序，保证跨进程恢复时 Blueprint 指纹一致。 */
    public AgentModelPolicy {
        if (Objects.nonNull(providers) && providers.stream().noneMatch(Objects::isNull)) providers = Collections.unmodifiableSet(
                providers.isEmpty() ? EnumSet.noneOf(AiProviderType.class) : EnumSet.copyOf(providers));
        if (Objects.nonNull(models) && models.stream().noneMatch(Objects::isNull)) models = Collections.unmodifiableSortedSet(new TreeSet<>(models));
    }
}
