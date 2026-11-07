package com.manga.agent.collaboration;

import com.manga.agent.profile.AgentProfileKey;
import com.manga.agent.profile.AgentProfileContracts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 一批封闭 DAG；依赖只能指向同批节点，不接受模型指定数据库 Step 标识。 */
public record DelegatePlanRequest(
        /** 用户可理解的协作计划名称。 */ @NotBlank @Size(max = 120) String title,
        /** 本轮 Worker 节点，实际数量还受 Run 预算约束。 */ @NotEmpty @Size(max = 16) List<@NotNull @Valid Node> nodes
) {
    /** 单个只读 Worker 的业务任务。 */
    public record Node(
            /** 批内稳定标识，用于声明依赖。 */ @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,47}") String key,
            /** 已启用发布的 Worker 配置身份。 */ @NotBlank @Pattern(regexp = AgentProfileContracts.KEY_PATTERN) String profile,
            /** 本节点要完成的具体目标。 */ @NotBlank @Size(max = 12000) String instruction,
            /** 同一计划内的前置节点标识。 */ @NotNull @Size(max = 16) List<@NotBlank String> dependsOn
    ) {
        /** 内置调用可继续使用枚举，动态标识来自已发布目录。 */
        public Node(String key, AgentProfileKey profile, String instruction, List<String> dependsOn) {
            this(key, profile.code(), instruction, dependsOn);
        }
    }
}
