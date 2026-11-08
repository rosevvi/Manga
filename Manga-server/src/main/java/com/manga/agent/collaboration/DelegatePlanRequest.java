package com.manga.agent.collaboration;

import com.manga.agent.profile.AgentProfileKey;
import com.manga.agent.profile.AgentProfileContracts;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

/** 一批封闭 DAG；依赖只能指向同批节点，不接受模型指定数据库 Step 标识。 */
public record DelegatePlanRequest(
        /** 用户可理解的协作计划名称。 */ @NotBlank @Size(max = 120) String title,
        /** 本轮 Worker 节点，实际数量还受 Run 预算约束。 */ @NotEmpty @Size(max = 16) List<@NotNull @Valid Node> nodes
) {
    /** 单个只读 Worker 的业务任务。 */
    public record Node(
            /** 批内稳定标识，用于声明依赖。 */ @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,47}") String key,
            /** 已发布配置身份，与 dynamicAgent 二选一。 */ @Pattern(regexp = AgentProfileContracts.KEY_PATTERN) String profile,
            /** 本节点要完成的具体目标。 */ @NotBlank @Size(max = 12000) String instruction,
            /** 同一计划内的前置节点标识。 */ @NotNull @Size(max = 16) List<@NotBlank String> dependsOn,
            /** 临时纯数据规格，不进入全局 Profile 目录。 */ @Valid @JsonInclude(JsonInclude.Include.NON_NULL) DynamicAgentRequest dynamicAgent
    ) {
        /** 现有发布配置的委派形式。 */
        public Node(String key, String profile, String instruction, List<String> dependsOn) {
            this(key, profile, instruction, dependsOn, null);
        }
        /** 严格要求一种来源，禁止同时覆盖已发布配置。 */
        @AssertTrue @JsonIgnore
        public boolean isSourceValid() { return Objects.nonNull(profile) != Objects.nonNull(dynamicAgent); }
        /** 内置调用可继续使用枚举，动态标识来自已发布目录。 */
        public Node(String key, AgentProfileKey profile, String instruction, List<String> dependsOn) {
            this(key, profile.code(), instruction, dependsOn, null);
        }
    }
}
