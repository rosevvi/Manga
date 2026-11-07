package com.manga.agent.profile;

import com.manga.common.api.ResponseCode;

/** 管理、发布和选择 Agent 配置时的业务响应。 */
public enum AgentProfileResponseCode implements ResponseCode {
    /** 严格定义、角色或模型约束非法。 */ INVALID("AGENT_PROFILE_INVALID", "Agent 定义、能力或模型策略不符合约束"),
    /** 配置不存在或不可用于当前入口。 */ UNAVAILABLE("AGENT_PROFILE_UNAVAILABLE", "Agent 不存在、未发布、已禁用或不支持当前入口"),
    /** 并发编辑后客户端版本已经过期。 */ CONFLICT("AGENT_PROFILE_CONFLICT", "Agent 配置已变化，请刷新后重试"),
    /** 已发布内容不得原地修改。 */ IMMUTABLE("AGENT_PROFILE_IMMUTABLE", "已发布版本不可修改，请先创建新草稿"),
    /** 机器标识已经存在。 */ DUPLICATED("AGENT_PROFILE_DUPLICATED", "Agent 标识已存在"),
    /** 模型未配置、停用或协议尚未接入。 */ MODEL_UNAVAILABLE("AGENT_PROFILE_MODEL_UNAVAILABLE", "当前用户的模型配置不可用，请检查默认模型、启用状态和对话协议支持"),
    /** 可信持久化数据无法恢复。 */ CORRUPTED("AGENT_PROFILE_CORRUPTED", "Agent 定义无法恢复，请检查发布记录");
    /** HTTP 返回的稳定业务编码。 */
    private final String code;
    /** 可以公开的错误说明。 */
    private final String message;
    AgentProfileResponseCode(String code, String message) { this.code = code; this.message = message; }
    /** 返回稳定业务编码。 */
    public String code() { return code; }
    /** 返回脱敏业务说明。 */
    public String message() { return message; }
}
