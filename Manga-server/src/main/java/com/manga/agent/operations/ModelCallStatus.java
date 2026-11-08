package com.manga.agent.operations;

/** 用量账本的调用终态与恢复标记，终态不再自动重发请求。 */
public enum ModelCallStatus {
    /** 调用已持久化受理，尚未关闭。 */ STARTED,
    /** 正常完成，包括供应商未返回 usage 的情况。 */ SUCCEEDED,
    /** 供应商调用失败，可能已经产生部分费用。 */ FAILED,
    /** 下游取消了调用，不能假定供应商已停止计费。 */ CANCELLED,
    /** 进程失联或租约超时，不能确定供应商结果。 */ UNKNOWN
}
