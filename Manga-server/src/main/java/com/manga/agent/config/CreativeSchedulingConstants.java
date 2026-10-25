package com.manga.agent.config;

/**
 * 集中维护创作运行时定时任务使用的配置占位符。
 */
public final class CreativeSchedulingConstants {

    /** Dispatch Outbox 发布周期配置。 */
    public static final String DISPATCH_OUTBOX_INTERVAL = "${manga.creative.dispatch-outbox.publish-interval:1s}";
    /** Run 和 Step 对账周期配置。 */
    public static final String RECONCILIATION_INTERVAL = "${manga.creative.reconciliation.interval:5s}";
    /** 消息投影补偿和内部状态清理周期。 */
    public static final String MEMORY_MAINTENANCE_INTERVAL = "${manga.creative.memory.maintenance-interval:1m}";

    /** 禁止实例化常量类。 */
    private CreativeSchedulingConstants() {
    }
}
