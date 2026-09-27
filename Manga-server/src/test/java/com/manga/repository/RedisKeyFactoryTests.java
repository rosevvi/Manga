package com.manga.repository;

import com.manga.config.properties.MangaRedisProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Creative Run Redis 频道由同一工厂生成并可逆解析。
 */
class RedisKeyFactoryTests {

    private static final String RUN_ID = "run-test";

    @Test
    void shouldBuildPatternAndParseCreativeRunChannel() {
        RedisKeyFactory factory = new RedisKeyFactory(new MangaRedisProperties("manga", Duration.ofMinutes(5)));

        assertThat(factory.creativeRunEventChannel(RUN_ID)).isEqualTo("manga:creative:run:run-test:events");
        assertThat(factory.creativeRunEventChannelPattern()).isEqualTo("manga:creative:run:*:events");
        assertThat(factory.creativeRunIdFromEventChannel(factory.creativeRunEventChannel(RUN_ID)))
                .contains(RUN_ID);
    }

    @Test
    void shouldRejectForeignOrEmptyCreativeRunChannel() {
        RedisKeyFactory factory = new RedisKeyFactory(new MangaRedisProperties("manga", Duration.ofMinutes(5)));

        assertThat(factory.creativeRunIdFromEventChannel("other:creative:run:run-test:events")).isEmpty();
        assertThat(factory.creativeRunIdFromEventChannel("manga:creative:run::events")).isEmpty();
    }
}
