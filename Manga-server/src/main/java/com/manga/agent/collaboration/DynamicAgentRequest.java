package com.manga.agent.collaboration;

import com.manga.agent.profile.AgentProfileKey;
import com.manga.agent.capability.CapabilityName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.Set;
import java.util.EnumSet;
import java.util.Collections;
import java.util.Objects;

/** 模型只能声明只读专业 Worker 的纯数据规格，不能指定模型、连接或执行代码。 */
public record DynamicAgentRequest(
        /** 用户可见专业名称。 */ @NotBlank @Size(max = 120) String name,
        /** 创建该专业分工的业务原因。 */ @NotBlank @Size(max = 1000) String reason,
        /** 固定 Worker 角色族，不允许 Director。 */ @NotNull AgentProfileKey role,
        /** 临时专业提示词，仍受平台权限约束。 */ @NotBlank @Size(max = 6000) String systemPrompt,
        /** 请求的现有业务读取能力，实际取父白名单交集。 */ @NotNull @Size(max = 4) Set<@NotNull CapabilityName> capabilities,
        /** 单次推理轮数上限。 */ @Min(1) @Max(8) int maxTurns
) {
    /** 固定枚举集合顺序，保障重放指纹稳定。 */
    public DynamicAgentRequest {
        if (Objects.nonNull(capabilities) && capabilities.stream().noneMatch(Objects::isNull)) {
            capabilities = Collections.unmodifiableSet(capabilities.isEmpty() ? EnumSet.noneOf(CapabilityName.class) : EnumSet.copyOf(capabilities));
        }
    }
}
