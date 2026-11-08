package com.manga.agent.operations;

/** 运维入口、调度和安全错误契约，不包含请求正文或供应商凭据。 */
public final class RuntimeOperationsConstants {
    /** 管理员运维接口前缀。 */
    public static final String ADMIN_PATH = "/api/v1/admin/creative-operations";
    /** 普通用户用量查询路径。 */
    public static final String USER_PATH = "/api/v1/creative-runs/{runId}/usage";
    /** 对账任务周期表达式。 */
    public static final String SCAN_INTERVAL = "${manga.creative.operations.scan-interval:30000}";
    /** 调用保护拒绝的安全描述。 */
    public static final String REJECTED = "模型供应商容量、速率、规划配额或熔断限制已触发，请稍后创建新 Run";
    /** 调用已过截止，禁止发出新的供应商请求。 */
    public static final String CALL_EXPIRED = "模型调用已超过运行截止时间";
    /** 限流、容量、规划配额或熔断保护拒绝次数。 */
    public static final String REJECTIONS_METRIC = "manga.model.rejected";
    /** 启动配置错误。 */
    public static final String INVALID_DURATION = "运维熔断、请求超时与归档时长必须为正数";
    /** 对账冲突或未关闭的调用。 */
    public static final String SETTLEMENT_CONFLICT = "调用尚未关闭、未找到或已经按其他账单对账";
    /** 尚不具备完整归档条件。 */
    public static final String INVALID_ARCHIVE = "运行尚未完成投影、存在活动作业或归档超过容量限制";
    /** 归档损坏，禁止作为有效副本导出。 */
    public static final String ARCHIVE_MISMATCH = "归档摘要校验失败";
    /** 自动归档最多约 16 Mi 字符，较大 Run 转离线备份。 */
    public static final int MAX_ARCHIVE_CHARACTERS = 16 * 1024 * 1024;
    /** 按有限终态统计调用次数。 */
    public static final String CALLS_METRIC = "manga.model.calls";
    /** 按有限终态统计调用时长。 */
    public static final String LATENCY_METRIC = "manga.model.duration";
    /** 对账和归档任务失败计数。 */
    public static final String FAILURES_METRIC = "manga.operations.failures";
    /** 最近 100 个配置中仍在熔断冷却的数量；完整状态保存在数据库。 */
    public static final String OPEN_CIRCUITS_METRIC = "manga.provider.open.circuits";
    private RuntimeOperationsConstants() { }
}
