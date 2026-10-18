package com.manga.agent.domain;

import java.util.Arrays;

/**
 * 定义 Agent 在一次 Run 内允许使用的最高业务权限。
 */
public enum AgentPermissionPolicy {

    /** 只允许读取和分析业务数据。 */
    READ_ONLY(0),
    /** 允许创建变更提案，但不能应用。 */
    PROPOSE_CHANGES(10),
    /** 允许在用户明确确认后应用变更或提交高成本作业。 */
    CONFIRM_SIDE_EFFECTS(20),
    /** 在用户授权范围和预算内执行受限自动化。 */
    AUTOMATION(30);

    /** 权限层级，仅用于显式权限包含判断。 */
    private final int authorityLevel;

    AgentPermissionPolicy(int authorityLevel) {
        this.authorityLevel = authorityLevel;
    }

    /** 返回用于数据库和 API 的稳定机器编码。 */
    public String code() {
        return name();
    }

    /** 判断当前策略是否覆盖能力要求的最低权限。 */
    public boolean allows(AgentPermissionPolicy required) {
        return required != null && authorityLevel >= required.authorityLevel;
    }

    /** 按稳定机器编码解析权限策略，拒绝未知编码。 */
    public static AgentPermissionPolicy fromCode(String code) {
        return Arrays.stream(values())
                .filter(policy -> policy.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        CreativeRuntimeMessages.UNKNOWN_PERMISSION_POLICY_TEMPLATE.formatted(code)));
    }
}
