package com.manga.agent.changeset;

import com.manga.common.api.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 提案、审批和恢复的稳定错误契约。 */
@Getter
@RequiredArgsConstructor
public enum ChangeSetResponseCode implements ResponseCode {
    /** 不暴露其他用户的提案或项目。 */
    NOT_FOUND("CHANGESET_NOT_FOUND", "提案不存在或无权访问"),
    /** Schema、范围或数量校验失败。 */
    INVALID("CHANGESET_INVALID", "提案内容、资源范围或数量不合法"),
    /** 请求不符合聚合状态迁移约束。 */
    STATE_CONFLICT("APPROVAL_STATE_CONFLICT", "审批状态已变化，请刷新后重试"),
    /** 禁止以过期生成输入覆盖新业务数据。 */
    REVISION_CONFLICT("REVISION_CONFLICT", "项目或章节已变化，请基于最新内容重新生成"),
    /** 失去执行权的模型不能再提交提案。 */
    OWNER_LOST("CHANGESET_OWNER_LOST", "执行权已失效，无法提交提案"),
    /** 同一恢复步骤不能再次提交业务提案。 */
    ALREADY_PROPOSED("CHANGESET_ALREADY_PROPOSED", "本步骤已提交提案，请说明审批结果；新的修改需要新一轮请求"),
    /** 持久化 JSON 无法按已冻结的 Schema 恢复。 */
    CORRUPTED("CHANGESET_CORRUPTED", "提案或审批检查点损坏");

    /** 稳定接口编码。 */
    private final String code;
    /** 可公开的异常描述。 */
    private final String message;

    /** 返回稳定业务编码。 */
    @Override
    public String code() { return code; }

    /** 返回默认客户端提示。 */
    @Override
    public String message() { return message; }
}
