package com.manga.agent.harness;

import java.util.List;

/** 返回字段路径与约束，不返回拒绝值、原始 JSON 或内部异常。 */
public final class ToolInputValidationException extends RuntimeException {
    /** 公开说明和工具错误编码的单一来源。 */ public static final String MESSAGE = "工具输入不符合结构要求，请按字段约束修正后再提交";
    /** 工具可理解的稳定错误类型。 */ public static final String CODE = "TOOL_INPUT_INVALID";
    private final List<FieldIssue> issues;
    public ToolInputValidationException(List<FieldIssue> issues) { super(MESSAGE); this.issues = List.copyOf(issues); }
    /** 返回安全字段约束，供模型修正和公开时间线显示。 */
    public List<FieldIssue> issues() { return issues; }
    /** 不包含原始输入的字段校验说明。 */
    public record FieldIssue(/** JSON 字段路径。 */ String path, /** 字段应满足的约束。 */ String constraint) { }
}
