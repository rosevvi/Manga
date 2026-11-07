package com.manga.agent.profile;

/** 服务端受理时使用的选择信息；精确版本和沙箱标记仅由管理员测试入口产生。 */
public record AgentProfileSelection(
        /** 数据库配置标识。 */ String profileId,
        /** 普通运行为空并解析当前发布指针；测试冻结明确版本。 */ Long version,
        /** 当前用户的模型配置主键。 */ Long modelConfigId,
        /** 是否为管理员只读测试。 */ boolean sandbox,
        /** 精确测试定义指纹，普通发布选择为空。 */ String definitionFingerprint
) {
    /** 普通用户选择当前发布指针。 */
    public AgentProfileSelection(String profileId, Long version, Long modelConfigId, boolean sandbox) {
        this(profileId, version, modelConfigId, sandbox, null);
    }
}
