package com.manga.agent.domain;

/**
 * 集中维护创作运行时校验和内部故障使用的非敏感消息。
 */
public final class CreativeRuntimeMessages {

    // 请求参数与入口范围校验。
    /** 发起用户主键不满足正整数约束时的校验消息。 */
    public static final String USER_ID_MUST_BE_POSITIVE = "创作运行用户主键必须大于零";
    /** 缺少启动幂等标识时的校验消息。 */
    public static final String REQUEST_ID_REQUIRED = "创作运行请求标识不能为空";
    /** 未指定会话或手动触发来源时的校验消息。 */
    public static final String TRIGGER_SOURCE_REQUIRED = "创作运行触发来源不能为空";
    /** 未指定业务操作类型时的校验消息。 */
    public static final String OPERATION_TYPE_REQUIRED = "创作操作类型不能为空";
    /** 未指定 Agent 权限策略时的校验消息。 */
    public static final String PERMISSION_POLICY_REQUIRED = "Agent 权限策略不能为空";
    /** 自然语言执行指令为空时的校验消息。 */
    public static final String INSTRUCTION_REQUIRED = "创作运行指令不能为空";
    /** 项目主键不满足正整数约束时的校验消息。 */
    public static final String PROJECT_ID_MUST_BE_POSITIVE = "项目主键必须大于零";
    /** 手动操作缺少直接目标资源时的校验消息。 */
    public static final String TARGET_REQUIRED_FOR_MANUAL = "手动创作操作必须指定目标资源";
    /** 会话入口携带手动操作目标时的校验消息。 */
    public static final String CONVERSATION_CANNOT_HAVE_TARGET = "助手会话操作不能指定直接目标资源";
    /** 手动入口携带会话标识时的校验消息。 */
    public static final String MANUAL_CONVERSATION_NOT_ALLOWED = "手动创作操作不能绑定助手会话";
    /** 手动操作缺少所属项目时的校验消息。 */
    public static final String MANUAL_PROJECT_REQUIRED = "手动创作操作必须绑定项目";
    /** 手动入口误用助手对话操作时的校验消息。 */
    public static final String MANUAL_CHAT_NOT_ALLOWED = "手动入口不能使用助手会话操作类型";
    /** 会话入口使用非助手对话操作时的校验消息。 */
    public static final String CONVERSATION_OPERATION_INVALID = "会话入口只能使用助手会话操作类型";
    /** API 请求跨字段入口约束不成立时的统一校验消息。 */
    public static final String REQUEST_SCOPE_INVALID = "触发来源、操作类型、会话和目标资源组合不合法";
    /** SSE 游标无法解析或不符合运行范围时的校验消息。 */
    public static final String INVALID_EVENT_CURSOR = "SSE 事件游标格式不合法";
    /** 直接目标缺少业务资源类型时的校验消息。 */
    public static final String RESOURCE_TYPE_REQUIRED = "创作目标资源类型不能为空";
    /** 直接目标主键不满足正整数约束时的校验消息。 */
    public static final String RESOURCE_ID_MUST_BE_POSITIVE = "创作目标资源主键必须大于零";
    /** 请求项目与会话既有项目绑定冲突时的拒绝消息。 */
    public static final String CONVERSATION_PROJECT_CONFLICT = "会话已绑定其他项目";

    // Run、事件载荷与请求指纹。
    /** 查询或追加事件时找不到所属 Run 的故障消息。 */
    public static final String RUN_NOT_FOUND = "创作 Run 不存在";
    /** 持久化 JSON 载荷无法恢复为运行契约时的故障消息。 */
    public static final String EVENT_PAYLOAD_CORRUPTED = "创作事件载荷损坏";
    /** 事件或 Step 输入无法序列化为 JSON 时的故障消息。 */
    public static final String EVENT_PAYLOAD_SERIALIZATION_FAILED = "创作事件载荷序列化失败";
    /** 幂等请求指纹材料无法序列化时的故障消息。 */
    public static final String REQUEST_FINGERPRINT_SERIALIZATION_FAILED = "创作请求指纹材料序列化失败";
    /** 运行环境不支持指纹所需摘要算法时的故障消息。 */
    public static final String SHA_256_UNAVAILABLE = "SHA-256 摘要算法不可用";

    // Agent Profile 注册与恢复。
    /** Profile 定义或提示词资源不可用；参数为 Profile 标识或资源位置。 */
    public static final String AGENT_PROFILE_NOT_FOUND_TEMPLATE = "Agent Profile 不存在：%s";
    /** 无法将机器编码解析为 Profile；参数为未知编码。 */
    public static final String AGENT_PROFILE_UNKNOWN_TEMPLATE = "未知 Agent Profile 编码：%s";
    /** 相同 Profile 标识被重复注册；参数为重复标识。 */
    public static final String AGENT_PROFILE_DUPLICATED_TEMPLATE = "Agent Profile 重复注册：%s";

    // Capability 注册、输入和授权。
    /** 能力注册表中缺少指定能力；参数为能力标识。 */
    public static final String CAPABILITY_NOT_FOUND_TEMPLATE = "Agent Capability 不存在：%s";
    /** 无法将机器编码解析为能力标识；参数为未知编码。 */
    public static final String CAPABILITY_UNKNOWN_TEMPLATE = "未知 Agent Capability 编码：%s";
    /** 相同能力标识被重复注册；参数为重复标识。 */
    public static final String CAPABILITY_DUPLICATED_TEMPLATE = "Agent Capability 重复注册：%s";
    /** 工具调用超出冻结的能力白名单；参数为能力标识。 */
    public static final String CAPABILITY_NOT_ALLOWED_TEMPLATE = "Agent Capability 未被当前 Blueprint 授权：%s";
    /** 项目读取能力缺少可信项目上下文时的拒绝消息。 */
    public static final String CAPABILITY_PROJECT_REQUIRED = "当前只读能力要求 Run 绑定项目";
    /** 目标资源不存在或不在用户可访问范围时的统一拒绝消息。 */
    public static final String CAPABILITY_RESOURCE_UNAVAILABLE = "目标项目资源不存在或当前用户无权访问";
    /** 工具参数缺少必填字段；参数为字段名。 */
    public static final String CAPABILITY_INPUT_REQUIRED_TEMPLATE = "Agent Capability 缺少输入字段：%s";
    /** 工具参数不满足字段约束；参数为字段名。 */
    public static final String CAPABILITY_INPUT_INVALID_TEMPLATE = "Agent Capability 输入字段不合法：%s";
    /** 工具结果无法序列化为模型可读载荷时的故障消息。 */
    public static final String CAPABILITY_RESULT_SERIALIZATION_FAILED = "Agent Capability 结果序列化失败";
    /** 工具调用缺少可信请求级上下文时的拒绝消息。 */
    public static final String CAPABILITY_RUNTIME_CONTEXT_REQUIRED = "Agent Capability 缺少请求级运行上下文";
    /** 当前运行权限不足以调用能力；参数为能力标识。 */
    public static final String CAPABILITY_PERMISSION_DENIED_TEMPLATE = "Agent Capability 权限不足：%s";
    /** 实际 Toolkit 与冻结能力清单不一致时的预检消息。 */
    public static final String CAPABILITY_TOOLKIT_MISMATCH = "Agent Toolkit 与 Blueprint 能力白名单不一致";

    // 模型配置与资源生命周期。
    /** 指定或默认模型配置不存在、不可访问或已停用时的故障消息。 */
    public static final String AGENT_MODEL_CONFIG_UNAVAILABLE = "当前用户没有可用的默认文本模型配置";
    /** 模型配置缺少模型名称时的校验消息。 */
    public static final String AGENT_MODEL_NAME_REQUIRED = "Agent 模型编码不能为空";
    /** 模型供应商没有对应适配器；参数为供应商类型。 */
    public static final String AGENT_MODEL_PROVIDER_UNSUPPORTED_TEMPLATE = "Agent 模型服务商暂不支持：%s";
    /** 同一供应商存在重复适配器；参数为供应商类型。 */
    public static final String AGENT_MODEL_PROVIDER_DUPLICATED_TEMPLATE = "Agent 模型服务商重复注册：%s";
    /** 当前模型配置安全指纹与冻结快照不一致时的恢复拒绝消息。 */
    public static final String AGENT_MODEL_SECURITY_FINGERPRINT_MISMATCH = "Agent 模型配置已变化，无法按原 Blueprint 恢复";
    /** 创建模型客户端时缺少可用 API Key 的故障消息。 */
    public static final String AGENT_MODEL_API_KEY_REQUIRED = "Agent 模型配置缺少 API Key";
    /** 释放模型客户端或其底层连接失败时的故障消息。 */
    public static final String AGENT_MODEL_CLOSE_FAILED = "关闭 Agent 模型资源失败";

    // 状态持久化与会话互斥。
    /** 状态存储或会话闸门缺少 StateSession 标识时的校验消息。 */
    public static final String AGENT_STATE_SESSION_ID_REQUIRED = "Agent 状态会话标识不能为空";
    /** 同一状态会话已被其他执行器占用时的拒绝消息。 */
    public static final String AGENT_STATE_SESSION_BUSY = "Agent 状态会话正在由其他执行器使用";
    /** 会话互斥服务不可用时禁止继续调用模型的故障消息。 */
    public static final String AGENT_STATE_SESSION_GATE_UNAVAILABLE = "Agent 状态会话闸门不可用";
    /** 会话闸门租约失效或续签失败时的中断消息。 */
    public static final String AGENT_STATE_SESSION_LEASE_LOST = "Agent 状态会话租约已丢失";
    /** 状态存储缺少用户隔离键时的校验消息。 */
    public static final String AGENT_STATE_USER_KEY_REQUIRED = "Agent 状态用户隔离键不能为空";
    /** 状态存储缺少组件状态键时的校验消息。 */
    public static final String AGENT_STATE_KEY_REQUIRED = "Agent 组件状态键不能为空";
    /** 状态对象、列表或类型为空时的校验消息。 */
    public static final String AGENT_STATE_VALUE_REQUIRED = "Agent 状态值不能为空";
    /** 状态写入携带不合法乐观版本时的校验消息。 */
    public static final String AGENT_STATE_VERSION_INVALID = "Agent 状态期望版本不合法";
    /** 状态 JSON、类型或载荷种类无法序列化或恢复时的故障消息。 */
    public static final String AGENT_STATE_PAYLOAD_INVALID = "Agent 状态载荷无法序列化或恢复";

    // Kernel、Harness 与执行规格。
    /** KernelPool 进入关闭排空阶段后拒绝新获取请求的消息。 */
    public static final String AGENT_KERNEL_POOL_DRAINING = "Agent KernelPool 正在关闭";
    /** 共享 Kernel 或活跃 Harness 数量达到容量限制时的拒绝消息。 */
    public static final String AGENT_KERNEL_CAPACITY_EXHAUSTED = "Agent KernelPool 暂无可用容量";
    /** 使用已释放 KernelHandle 打开 Harness 时的拒绝消息。 */
    public static final String AGENT_KERNEL_HANDLE_CLOSED = "KernelHandle 已释放";
    /** 同一 HarnessLease 被重复调用时的拒绝消息。 */
    public static final String AGENT_HARNESS_ALREADY_INVOKED = "HarnessLease 只允许执行一次调用";
    /** Harness 调用缺少自然语言指令时的校验消息。 */
    public static final String AGENT_INVOCATION_INSTRUCTION_REQUIRED = "Agent 调用指令不能为空";
    /** Blueprint 快照或指纹材料无法序列化时的故障消息。 */
    public static final String AGENT_BLUEPRINT_SERIALIZATION_FAILED = "Agent Blueprint 序列化失败";
    /** Blueprint 内容、指纹或运行契约校验不一致时的拒绝消息。 */
    public static final String AGENT_BLUEPRINT_CONTRACT_MISMATCH = "Agent Blueprint 与当前运行契约不一致";
    /** 请求权限与 Profile 允许权限不匹配时的拒绝消息。 */
    public static final String AGENT_PERMISSION_EXCEEDS_PROFILE = "请求权限超过 Agent Profile 允许范围";

    // Agent Step 执行、租约和业务操作绑定。
    /** Agent Step 未持久化 Blueprint 快照时的恢复拒绝消息。 */
    public static final String AGENT_STEP_BLUEPRINT_REQUIRED = "Agent Step 缺少已冻结的 Blueprint";
    /** Agent Step 未持久化独立状态会话标识时的恢复拒绝消息。 */
    public static final String AGENT_STEP_STATE_SESSION_REQUIRED = "Agent Step 缺少状态会话标识";
    /** 完整 Agent 输出无法序列化为 Step 结果时的故障消息。 */
    public static final String AGENT_STEP_OUTPUT_SERIALIZATION_FAILED = "Agent Step 输出序列化失败";
    /** Step 租约失效或 owner epoch 围栏校验失败时的中断消息。 */
    public static final String AGENT_STEP_OWNER_LEASE_LOST = "Agent Step 执行租约已丢失";
    /** 心跳配置的租约时长为空或非正数时的校验消息。 */
    public static final String AGENT_STEP_LEASE_DURATION_INVALID = "Agent Step 租约时长必须大于零";
    /** Step 缺少运行期限或已到达截止时间时的拒绝消息。 */
    public static final String AGENT_STEP_DEADLINE_EXCEEDED = "Agent Step 已超过运行期限";
    /** 模型工具循环达到 Profile 最大推理轮次时的失败消息。 */
    public static final String AGENT_MAX_TURNS_EXCEEDED = "Agent 已达到最大推理轮次";
    /** 未分类执行异常对外保存的非敏感失败摘要。 */
    public static final String AGENT_STEP_EXECUTION_FAILED = "Agent Step 执行失败";
    /** 同一 Step 类型重复注册执行器；参数为 Step 类型。 */
    public static final String AGENT_STEP_EXECUTOR_DUPLICATED_TEMPLATE = "Creative Step 执行器重复注册：%s";
    /** Step 类型没有对应执行器；参数为 Step 类型。 */
    public static final String AGENT_STEP_EXECUTOR_NOT_FOUND_TEMPLATE = "Creative Step 执行器未注册：%s";
    /** 操作目标类型不匹配；参数依次为业务操作、要求的目标资源类型。 */
    public static final String AGENT_OPERATION_TARGET_TYPE_INVALID_TEMPLATE = "创作操作 %s 必须指定 %s 目标";
    /** 剧本目标不存在或与当前授权项目不一致时的拒绝消息。 */
    public static final String AGENT_OPERATION_SCRIPT_TARGET_INVALID = "剧本解析目标不存在或不属于当前项目";

    // 持久化与 API 枚举编码解析。
    /** 无法恢复 Run 状态；参数为未知状态编码。 */
    public static final String UNKNOWN_RUN_STATUS_TEMPLATE = "未知创作 Run 状态编码：%s";
    /** 无法恢复 Step 状态；参数为未知状态编码。 */
    public static final String UNKNOWN_STEP_STATUS_TEMPLATE = "未知创作 Step 状态编码：%s";
    /** 无法恢复事件类型；参数为未知事件编码。 */
    public static final String UNKNOWN_EVENT_TYPE_TEMPLATE = "未知创作事件类型编码：%s";
    /** 无法恢复错误类型；参数为未知错误编码。 */
    public static final String UNKNOWN_ERROR_CODE_TEMPLATE = "未知创作错误编码：%s";
    /** 无法恢复权限策略；参数为未知策略编码。 */
    public static final String UNKNOWN_PERMISSION_POLICY_TEMPLATE = "未知 Agent 权限策略编码：%s";

    /** 禁止实例化消息常量类。 */
    private CreativeRuntimeMessages() {
    }
}
