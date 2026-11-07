package com.manga.agent.profile;

import com.manga.agent.blueprint.ModelSnapshot;
import com.manga.common.exception.BusinessException;
import java.util.Objects;

/** 模型与入口策略的纯领域检查，不依赖能力注册表，避免编译器依赖环。 */
public final class AgentProfilePolicySupport {
    /** 只读根专业 Agent 和 Director 可以从会话入口选择。 */
    public static boolean rootSelectable(AgentProfile profile) {
        return (profile.key() == AgentProfileKey.MANGA_DIRECTOR || profile.key() == AgentProfileKey.SCRIPT_PARSER)
                && (Objects.isNull(profile.executionPolicy())
                || profile.executionPolicy().model().selection() != AgentModelSelection.INHERIT_PARENT);
    }

    /** 既约束根模型选择，也约束 Worker 继承后的模型。 */
    public static void requireModel(AgentModelPolicy policy, ModelSnapshot model) {
        if (Objects.isNull(policy)) return;
        if ((!policy.providers().isEmpty() && !policy.providers().contains(model.providerType()))
                || (!policy.models().isEmpty() && !policy.models().contains(model.modelName()))) {
            throw new BusinessException(AgentProfileResponseCode.INVALID);
        }
    }
    private AgentProfilePolicySupport() { }
}
