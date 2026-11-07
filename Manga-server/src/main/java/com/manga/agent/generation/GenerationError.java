package com.manga.agent.generation;
import com.manga.common.api.ResponseCode;
import lombok.RequiredArgsConstructor;
/** 生成链路公开错误及安全描述。 */
@RequiredArgsConstructor
public enum GenerationError implements ResponseCode {
    /** 非法生成参数或未配置价格。 */ INVALID("GENERATION_INVALID", "生成参数不合法，或供应商/模型/费用尚未配置"),
    /** 无权限访问。 */ NOT_FOUND("GENERATION_NOT_FOUND", "生成作业不存在或无权访问"),
    /** 状态竞争。 */ STATE("GENERATION_STATE_CONFLICT", "作业状态已变化，请刷新后重试"),
    /** 供应商明确拒绝。 */ REJECTED("GENERATION_REJECTED", "供应商拒绝生成请求，请检查模型、权限和参数"),
    /** 认证失效。 */ AUTHENTICATION("GENERATION_AUTHENTICATION", "图片供应商配置不可用或认证失败"),
    /** 限流，不做隐式重新扣费提交。 */ RATE_LIMIT("GENERATION_RATE_LIMIT", "供应商限流，请稍后发起新的生成请求"),
    /** 网络结果未知。 */ SUBMISSION_UNKNOWN("GENERATION_SUBMISSION_UNKNOWN", "提交结果不明确，已停止自动重提，请核对供应商作业"),
    /** 读取失败允许退避。 */ TRANSIENT("GENERATION_TRANSIENT", "供应商暂时不可访问，正在退避重试"),
    /** 供应商终态失败。 */ FAILED("GENERATION_FAILED", "供应商生成失败，未保存媒体产物"),
    /** 结果格式错误。 */ PROTOCOL("GENERATION_PROTOCOL", "供应商返回的任务或媒体结果不符合协议"),
    /** 保存失败允许仅重试转存。 */ STORAGE("GENERATION_STORAGE", "媒体转存未完成，将重试转存，不会重新提交生成"),
    /** 超过治理期限，不假定供应商已停止。 */ TIMEOUT("GENERATION_TIMEOUT", "作业超过监测期限，请核对供应商状态"),
    /** 回调验证失败。 */ CALLBACK("GENERATION_CALLBACK_INVALID", "生成回调签名、时间戳或内容无效");
    /** 稳定错误编码。 */ private final String code;
    /** 对用户安全的提示。 */ private final String message;
    /** 返回错误编码。 */ public String code() { return code; }
    /** 返回默认提示。 */ public String message() { return message; }
}
