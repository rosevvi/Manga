package com.manga.agent.profile;

/** 管理 API 与发布协议共用的稳定约束。 */
public final class AgentProfileContracts {
    /** 配置身份允许的格式，不允许类名、路径或表达式。 */
    public static final String KEY_PATTERN = "[a-z][a-z0-9_]{2,63}";
    /** 管理 API 的固定前缀。 */
    public static final String ADMIN_PATH = "/api/v1/admin/agent-profiles";
    /** 普通用户仅能读取启用的发布目录。 */
    public static final String CATALOG_PATH = "/api/v1/agent-profiles";
    /** 有界目录查询的最大数量。 */
    public static final int CATALOG_LIMIT = 500;
    /** 审计日志的游标分页条数。 */
    public static final int AUDIT_PAGE_SIZE = 50;
    /** 模型每次读取的专业目录条数，避免大量配置塞满上下文。 */
    public static final int TOOL_CATALOG_PAGE_SIZE = 12;
    /** Director 目录职责摘要长度，完整描述保留在管理界面。 */
    public static final int TOOL_DESCRIPTION_CHARACTERS = 320;
    /** 当前纯数据定义 schema 版本。 */
    public static final int SCHEMA_VERSION = 1;
    /** 管理员只读测试在系统提示词中明确覆盖 Worker 的正常结果提交流程。 */
    public static final String SANDBOX_PROMPT = "\n\n【管理员单 Agent 只读测试】本次用于检查提示词和读取能力。直接输出测试报告，不调用结果提交、委派或业务提案工具，不声称保存任何数据。平台只提供只读业务工具。";
    private AgentProfileContracts() { }
}
