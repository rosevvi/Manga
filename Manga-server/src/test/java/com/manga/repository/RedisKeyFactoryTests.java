package com.manga.repository;

import com.manga.config.properties.MangaRedisProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Creative Run Redis 频道由同一工厂稳定生成。
 */
class RedisKeyFactoryTests {

    private static final String RUN_ID = "run-test";

    @Test
    void shouldBuildCreativeRunChannel() {
        RedisKeyFactory factory = new RedisKeyFactory(new MangaRedisProperties("manga", Duration.ofMinutes(5)));

        assertThat(factory.creativeRunEventChannel(RUN_ID)).isEqualTo("manga:creative:run:run-test:events");
    }
}
