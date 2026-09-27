package com.manga.agent.domain;

/**
 * 定义后台任务写入数据库审计字段时使用的稳定系统操作者。
 */
public enum SystemActor {

    /** Creative Runtime 受理、执行和收敛任务。 */
    CREATIVE_RUNTIME("system:creative-runtime"),

    /** Event Outbox 发布任务。 */
    EVENT_OUTBOX("system:event-outbox"),

    /** 外部生成作业轮询任务。 */
    GENERATION_POLLER("system:generation-poller"),

    /** 数据库迁移和默认值回填任务。 */
    DATABASE_MIGRATION("system:database-migration");

    /** 写入审计字段的稳定操作者编码。 */
    private final String code;

    SystemActor(String code) {
        this.code = code;
    }

    /** 返回审计字段使用的稳定操作者编码。 */
    public String code() {
        return code;
    }
}
