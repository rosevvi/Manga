package com.manga.agent.domain;

/**
 * 统一生成创作运行审计字段使用的用户操作者标识。
 */
public final class CreativeActor {

    /** 用户操作者编码格式。 */
    private static final String USER_ACTOR_FORMAT = "user:%d";

    /** 禁止实例化无状态工具类。 */
    private CreativeActor() {
    }

    /** 根据用户主键生成稳定、可检索的审计操作者编码。 */
    public static String user(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.USER_ID_MUST_BE_POSITIVE);
        }
        return USER_ACTOR_FORMAT.formatted(userId);
    }
}
