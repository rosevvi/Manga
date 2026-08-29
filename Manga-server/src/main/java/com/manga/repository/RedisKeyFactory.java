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

    private static final String WECHAT_ACCESS_TOKEN_KEY = "%s:wechat:access-token:%s";
    private static final String WECHAT_LOGIN_TOKEN_KEY = "%s:wechat:login-token:%s";
    private static final String WECHAT_LOGIN_SCENE_KEY = "%s:wechat:login-scene:%s";

    private final MangaRedisProperties properties;

    public String wechatAccessToken(String appId) {
        return WECHAT_ACCESS_TOKEN_KEY.formatted(properties.keyPrefix(), appId);
    }

    public String wechatLoginToken(String loginToken) {
        return WECHAT_LOGIN_TOKEN_KEY.formatted(properties.keyPrefix(), loginToken);
    }

    public String wechatLoginScene(String sceneKey) {
        return WECHAT_LOGIN_SCENE_KEY.formatted(properties.keyPrefix(), sceneKey);
    }
}
