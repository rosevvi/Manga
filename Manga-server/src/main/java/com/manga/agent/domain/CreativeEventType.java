package com.manga.agent.domain;

import java.util.Arrays;

/**
 * 定义创作运行事件日志和 SSE 协议使用的稳定事件类型。
 */
public enum CreativeEventType {

    /** Run 已完成持久化受理。 */
    RUN_ACCEPTED,
    /** Run 开始执行。 */
    RUN_STARTED,
    /** 工作流计划已经创建。 */
    PLAN_CREATED,
    /** Step 已创建。 */
    STEP_CREATED,
    /** Step 开始执行。 */
    STEP_STARTED,
    /** Step 成功完成。 */
    STEP_COMPLETED,
    /** Step 执行失败。 */
    STEP_FAILED,
    /** 模型输出一段增量文本。 */
    CONTENT_DELTA,
    /** 工具调用开始。 */
    TOOL_STARTED,
    /** 工具调用完成。 */
    TOOL_COMPLETED,
    /** AI 已创建待审查变更集。 */
    CHANGESET_PROPOSED,
    /** 工作流需要用户审批。 */
    APPROVAL_REQUIRED,
    /** 用户已经作出审批决定。 */
    APPROVAL_DECIDED,
    /** 变更集已应用。 */
    CHANGESET_APPLIED,
    /** 变更集与当前业务版本冲突。 */
    CHANGESET_CONFLICTED,
    /** 外部生成作业已经提交。 */
    JOB_SUBMITTED,
    /** 外部生成作业进度更新。 */
    JOB_PROGRESS,
    /** 外部生成作业成功完成。 */
    JOB_COMPLETED,
    /** 外部生成作业失败。 */
    JOB_FAILED,
    /** 新的创作产物已经入库。 */
    ARTIFACT_CREATED,
    /** Run 已收到取消请求。 */
    RUN_CANCEL_REQUESTED,
    /** Run 已完成取消。 */
    RUN_CANCELLED,
    /** Run 成功完成。 */
    RUN_COMPLETED,
    /** Run 执行失败。 */
    RUN_FAILED;

    /** 返回用于数据库和事件协议的稳定机器编码。 */
    public String code() {
        return name();
    }

    /** 按稳定机器编码解析事件类型，拒绝未知编码。 */
    public static CreativeEventType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        CreativeRuntimeMessages.UNKNOWN_EVENT_TYPE_TEMPLATE.formatted(code)));
    }
}
