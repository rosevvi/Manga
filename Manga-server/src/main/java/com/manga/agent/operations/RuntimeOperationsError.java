package com.manga.agent.operations;

import com.manga.common.api.ResponseCode;
import lombok.RequiredArgsConstructor;

/** 运维公开失败契约，与供应商响应或用户正文解耦。 */
@RequiredArgsConstructor
public enum RuntimeOperationsError implements ResponseCode {
    /** 不允许覆盖已确认账单或确认仍在执行的调用。 */
    SETTLEMENT_CONFLICT("MODEL_SETTLEMENT_CONFLICT", RuntimeOperationsConstants.SETTLEMENT_CONFLICT),
    /** 归档条件未满足或记录超过在线打包边界。 */
    ARCHIVE_NOT_READY("CREATIVE_ARCHIVE_NOT_READY", RuntimeOperationsConstants.INVALID_ARCHIVE),
    /** 归档副本损坏，禁止继续导出或作为恢复依据。 */
    ARCHIVE_CORRUPTED("CREATIVE_ARCHIVE_CORRUPTED", RuntimeOperationsConstants.ARCHIVE_MISMATCH);

    /** 稳定业务编码。 */
    private final String code;
    /** 可安全公开的说明。 */
    private final String message;
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
