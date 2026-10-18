package com.manga.agent.domain;

/**
 * 集中维护创作运行时校验和内部故障使用的非敏感消息。
 */
public final class CreativeRuntimeMessages {

    public static final String USER_ID_MUST_BE_POSITIVE = "创作运行用户主键必须大于零";
    public static final String REQUEST_ID_REQUIRED = "创作运行请求标识不能为空";
    public static final String TRIGGER_SOURCE_REQUIRED = "创作运行触发来源不能为空";
    public static final String OPERATION_TYPE_REQUIRED = "创作操作类型不能为空";
    public static final String PERMISSION_POLICY_REQUIRED = "Agent 权限策略不能为空";
    public static final String INSTRUCTION_REQUIRED = "创作运行指令不能为空";
    public static final String PROJECT_ID_MUST_BE_POSITIVE = "项目主键必须大于零";
    public static final String TARGET_REQUIRED_FOR_MANUAL = "手动创作操作必须指定目标资源";
    public static final String CONVERSATION_CANNOT_HAVE_TARGET = "助手会话操作不能指定直接目标资源";
    public static final String MANUAL_CONVERSATION_NOT_ALLOWED = "手动创作操作不能绑定助手会话";
    public static final String MANUAL_PROJECT_REQUIRED = "手动创作操作必须绑定项目";
    public static final String MANUAL_CHAT_NOT_ALLOWED = "手动入口不能使用助手会话操作类型";
    public static final String CONVERSATION_OPERATION_INVALID = "会话入口只能使用助手会话操作类型";
    public static final String REQUEST_SCOPE_INVALID = "触发来源、操作类型、会话和目标资源组合不合法";
    public static final String INVALID_EVENT_CURSOR = "SSE 事件游标格式不合法";
    public static final String RESOURCE_TYPE_REQUIRED = "创作目标资源类型不能为空";
    public static final String RESOURCE_ID_MUST_BE_POSITIVE = "创作目标资源主键必须大于零";
    public static final String CONVERSATION_PROJECT_CONFLICT = "会话已绑定其他项目";
    public static final String RUN_NOT_FOUND = "创作 Run 不存在";
    public static final String EVENT_PAYLOAD_CORRUPTED = "创作事件载荷损坏";
    public static final String EVENT_PAYLOAD_SERIALIZATION_FAILED = "创作事件载荷序列化失败";
    public static final String REQUEST_FINGERPRINT_SERIALIZATION_FAILED = "创作请求指纹材料序列化失败";
    public static final String SHA_256_UNAVAILABLE = "SHA-256 摘要算法不可用";
    public static final String AGENT_PROFILE_NOT_FOUND_TEMPLATE = "Agent Profile 不存在：%s";
    public static final String AGENT_PROFILE_UNKNOWN_TEMPLATE = "未知 Agent Profile 编码：%s";
    public static final String AGENT_PROFILE_DUPLICATED_TEMPLATE = "Agent Profile 重复注册：%s";
    public static final String CAPABILITY_NOT_FOUND_TEMPLATE = "Agent Capability 不存在：%s";
    public static final String CAPABILITY_UNKNOWN_TEMPLATE = "未知 Agent Capability 编码：%s";
    public static final String CAPABILITY_DUPLICATED_TEMPLATE = "Agent Capability 重复注册：%s";
    public static final String CAPABILITY_NOT_ALLOWED_TEMPLATE = "Agent Capability 未被当前 Blueprint 授权：%s";
    public static final String CAPABILITY_PROJECT_REQUIRED = "当前只读能力要求 Run 绑定项目";
    public static final String CAPABILITY_RESOURCE_UNAVAILABLE = "目标项目资源不存在或当前用户无权访问";
    public static final String CAPABILITY_INPUT_REQUIRED_TEMPLATE = "Agent Capability 缺少输入字段：%s";
    public static final String CAPABILITY_INPUT_INVALID_TEMPLATE = "Agent Capability 输入字段不合法：%s";
    public static final String CAPABILITY_RESULT_SERIALIZATION_FAILED = "Agent Capability 结果序列化失败";
    public static final String CAPABILITY_RUNTIME_CONTEXT_REQUIRED = "Agent Capability 缺少请求级运行上下文";
    public static final String CAPABILITY_PERMISSION_DENIED_TEMPLATE = "Agent Capability 权限不足：%s";
    public static final String CAPABILITY_TOOLKIT_MISMATCH = "Agent Toolkit 与 Blueprint 能力白名单不一致";
    public static final String AGENT_MODEL_CONFIG_UNAVAILABLE = "当前用户没有可用的默认文本模型配置";
    public static final String AGENT_MODEL_NAME_REQUIRED = "Agent 模型编码不能为空";
    public static final String AGENT_MODEL_PROVIDER_UNSUPPORTED_TEMPLATE = "Agent 模型服务商暂不支持：%s";
    public static final String AGENT_MODEL_PROVIDER_DUPLICATED_TEMPLATE = "Agent 模型服务商重复注册：%s";
    public static final String AGENT_MODEL_SECURITY_FINGERPRINT_MISMATCH = "Agent 模型配置已变化，无法按原 Blueprint 恢复";
    public static final String AGENT_MODEL_API_KEY_REQUIRED = "Agent 模型配置缺少 API Key";
    public static final String AGENT_MODEL_CLOSE_FAILED = "关闭 Agent 模型资源失败";
    public static final String AGENT_STATE_SESSION_ID_REQUIRED = "Agent 状态会话标识不能为空";
    public static final String AGENT_STATE_SESSION_BUSY = "Agent 状态会话正在由其他执行器使用";
    public static final String AGENT_STATE_SESSION_GATE_UNAVAILABLE = "Agent 状态会话闸门不可用";
    public static final String AGENT_STATE_SESSION_LEASE_LOST = "Agent 状态会话租约已丢失";
    public static final String AGENT_STATE_USER_KEY_REQUIRED = "Agent 状态用户隔离键不能为空";
    public static final String AGENT_STATE_KEY_REQUIRED = "Agent 组件状态键不能为空";
    public static final String AGENT_STATE_VALUE_REQUIRED = "Agent 状态值不能为空";
    public static final String AGENT_STATE_VERSION_INVALID = "Agent 状态期望版本不合法";
    public static final String AGENT_STATE_PAYLOAD_INVALID = "Agent 状态载荷无法序列化或恢复";
    public static final String AGENT_KERNEL_POOL_DRAINING = "Agent KernelPool 正在关闭";
    public static final String AGENT_KERNEL_CAPACITY_EXHAUSTED = "Agent KernelPool 暂无可用容量";
    public static final String AGENT_KERNEL_HANDLE_CLOSED = "KernelHandle 已释放";
    public static final String AGENT_HARNESS_ALREADY_INVOKED = "HarnessLease 只允许执行一次调用";
    public static final String AGENT_INVOCATION_INSTRUCTION_REQUIRED = "Agent 调用指令不能为空";
    public static final String AGENT_BLUEPRINT_SERIALIZATION_FAILED = "Agent Blueprint 序列化失败";
    public static final String AGENT_BLUEPRINT_CONTRACT_MISMATCH = "Agent Blueprint 与当前运行契约不一致";
    public static final String AGENT_PERMISSION_EXCEEDS_PROFILE = "请求权限超过 Agent Profile 允许范围";
    public static final String UNKNOWN_RUN_STATUS_TEMPLATE = "未知创作 Run 状态编码：%s";
    public static final String UNKNOWN_STEP_STATUS_TEMPLATE = "未知创作 Step 状态编码：%s";
    public static final String UNKNOWN_EVENT_TYPE_TEMPLATE = "未知创作事件类型编码：%s";
    public static final String UNKNOWN_ERROR_CODE_TEMPLATE = "未知创作错误编码：%s";
    public static final String UNKNOWN_PERMISSION_POLICY_TEMPLATE = "未知 Agent 权限策略编码：%s";

    /** 禁止实例化消息常量类。 */
    private CreativeRuntimeMessages() {
    }
}
