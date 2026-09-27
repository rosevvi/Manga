package com.manga.repository;

import com.manga.config.properties.MangaRedisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 统一生成 Manga 业务使用的 Redis Key，避免不同模块发生命名碰撞。
 */
@Component
@RequiredArgsConstructor
public class RedisKeyFactory {

    /** 微信 access_token 缓存键模板。 */
    private static final String WECHAT_ACCESS_TOKEN_KEY = "%s:wechat:access-token:%s";
    /** 微信登录令牌缓存键模板。 */
    private static final String WECHAT_LOGIN_TOKEN_KEY = "%s:wechat:login-token:%s";
    /** 微信登录场景缓存键模板。 */
    private static final String WECHAT_LOGIN_SCENE_KEY = "%s:wechat:login-scene:%s";
    /** 创作 Run 事件唤醒频道模板。 */
    private static final String CREATIVE_RUN_EVENT_CHANNEL = "%s:creative:run:%s:events";
    /** 创作 Run 事件唤醒频道订阅模式。 */
    private static final String CREATIVE_RUN_EVENT_CHANNEL_PATTERN = "%s:creative:run:*:events";
    /** 创作 Run 事件频道固定前缀模板。 */
    private static final String CREATIVE_RUN_EVENT_CHANNEL_PREFIX = "%s:creative:run:";
    /** 创作 Run 事件频道固定后缀。 */
    private static final String CREATIVE_RUN_EVENT_CHANNEL_SUFFIX = ":events";

    private final MangaRedisProperties properties;

    /** 生成微信公众号 access_token 缓存键。 */
    public String wechatAccessToken(String appId) {
        return WECHAT_ACCESS_TOKEN_KEY.formatted(properties.keyPrefix(), appId);
    }

    /** 生成微信登录令牌缓存键。 */
    public String wechatLoginToken(String loginToken) {
        return WECHAT_LOGIN_TOKEN_KEY.formatted(properties.keyPrefix(), loginToken);
    }

    /** 生成微信登录场景缓存键。 */
    public String wechatLoginScene(String sceneKey) {
        return WECHAT_LOGIN_SCENE_KEY.formatted(properties.keyPrefix(), sceneKey);
    }

    /** 生成指定 Run 的事件唤醒频道。 */
    public String creativeRunEventChannel(String runId) {
        return CREATIVE_RUN_EVENT_CHANNEL.formatted(properties.keyPrefix(), runId);
    }

    /** 生成订阅全部 Creative Run 事件唤醒频道的模式。 */
    public String creativeRunEventChannelPattern() {
        return CREATIVE_RUN_EVENT_CHANNEL_PATTERN.formatted(properties.keyPrefix());
    }

    /** 从合法的事件唤醒频道中解析 Run 标识。 */
    public Optional<String> creativeRunIdFromEventChannel(String channel) {
        String prefix = CREATIVE_RUN_EVENT_CHANNEL_PREFIX.formatted(properties.keyPrefix());
        if (!channel.startsWith(prefix) || !channel.endsWith(CREATIVE_RUN_EVENT_CHANNEL_SUFFIX)) {
            return Optional.empty();
        }
        int end = channel.length() - CREATIVE_RUN_EVENT_CHANNEL_SUFFIX.length();
        if (end <= prefix.length()) {
            return Optional.empty();
        }
        return Optional.of(channel.substring(prefix.length(), end));
    }
}
