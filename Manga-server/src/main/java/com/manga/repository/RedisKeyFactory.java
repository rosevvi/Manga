package com.manga.repository;

import com.manga.config.properties.MangaRedisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}
