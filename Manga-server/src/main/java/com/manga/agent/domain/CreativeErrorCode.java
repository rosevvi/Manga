package com.manga.agent.domain;

import java.util.Arrays;

/**
 * 定义创作运行可持久化、可检索的稳定失败编码。
 */
public enum CreativeErrorCode {

    /** 请求不满足创作运行契约。 */
    INVALID_REQUEST,
    /** 用户无权访问目标项目或资源。 */
    PERMISSION_DENIED,
    /** 同一幂等请求已经存在。 */
    DUPLICATE_REQUEST,
    /** 目标资源已有冲突的活跃 Run。 */
    ACTIVE_RUN_CONFLICT,
    /** 指定运行时尚未启用。 */
    RUNTIME_UNAVAILABLE,
    /** Run 固化的 Agent、模型或能力配置已经不可用。 */
    RUN_CONFIG_UNAVAILABLE,
    /** Agent 状态存储不可用，运行时已拒绝继续调用模型。 */
    STATE_STORE_UNAVAILABLE,
    /** 同一 Agent 状态会话已由其他执行租约独占。 */
    STATE_SESSION_BUSY,
    /** KernelPool 在限定时间内无法提供执行容量。 */
    KERNEL_CAPACITY_EXHAUSTED,
    /** 模型调用失败。 */
    MODEL_INVOCATION_FAILED,
    /** 模型调用达到总时限。 */ MODEL_TIMEOUT,
    /** 供应商数据流超过无响应时限。 */ MODEL_IDLE_TIMEOUT,
    /** 模型密钥无效或权限不足。 */ MODEL_AUTHENTICATION_FAILED,
    /** 供应商限流。 */ MODEL_RATE_LIMITED,
    /** 模型响应不符合协议。 */ MODEL_PROTOCOL_ERROR,
    /** 执行权失联且已发出模型请求，禁止自动重复计费。 */ MODEL_RESULT_UNCERTAIN,
    /** 手动生成结束时未产出所需业务提案。 */
    PROPOSAL_REQUIRED,
    /** 工具或能力执行失败。 */
    CAPABILITY_EXECUTION_FAILED,
    /** 执行器租约丢失。 */
    OWNER_LEASE_LOST,
    /** Run 或 Step 超过执行期限。 */
    DEADLINE_EXCEEDED,
    /** 状态转换不符合领域状态机。 */
    INVALID_STATE_TRANSITION,
    /** 用户取消了运行。 */
    CANCELLED_BY_USER,
    /** Agent 事件入口超过有界容量，执行已主动停止。 */
    EVENT_BACKPRESSURE_OVERFLOW,
    /** 未归类的内部执行错误。 */
    INTERNAL_ERROR,
    /** Run 冻结的步骤、修订或模型执行预算耗尽。 */ BUDGET_EXCEEDED,
    /** ALL_SUCCESS 依赖链中存在失败节点。 */ DEPENDENCY_FAILED,
    /** Worker 未提交合法结构化结果。 */ STEP_RESULT_REQUIRED,
    /** 连贯性评审未通过，禁止提交最终提案。 */ REVIEW_REQUIRED;

    /** 返回用于数据库和 API 的稳定机器编码。 */
    public String code() {
        return name();
    }

    /** 按稳定机器编码解析错误类型，拒绝未知编码。 */
    public static CreativeErrorCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(error -> error.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        CreativeRuntimeMessages.UNKNOWN_ERROR_CODE_TEMPLATE.formatted(code)));
    }
}
