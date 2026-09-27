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
    public static final String UNKNOWN_RUN_STATUS_TEMPLATE = "未知创作 Run 状态编码：%s";
    public static final String UNKNOWN_STEP_STATUS_TEMPLATE = "未知创作 Step 状态编码：%s";
    public static final String UNKNOWN_EVENT_TYPE_TEMPLATE = "未知创作事件类型编码：%s";
    public static final String UNKNOWN_ERROR_CODE_TEMPLATE = "未知创作错误编码：%s";
    public static final String UNKNOWN_PERMISSION_POLICY_TEMPLATE = "未知 Agent 权限策略编码：%s";

    /** 禁止实例化消息常量类。 */
    private CreativeRuntimeMessages() {
    }
}
