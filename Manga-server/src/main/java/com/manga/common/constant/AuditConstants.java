package com.manga.common.constant;

/**
 * 统一维护数据审计字段使用的技术常量。
 */
public final class AuditConstants {

    /** 系统自动操作的审计人标识。 */
    public static final String SYSTEM_ACTOR = "SYSTEM";
    /** 微信公众号操作的审计人标识。 */
    public static final String WECHAT_OFFICIAL_ACCOUNT_ACTOR = "WECHAT_OFFICIAL_ACCOUNT";

    /** 禁止实例化常量类。 */
    private AuditConstants() {
    }
}
