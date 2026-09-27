package com.manga.agent.event;

import com.manga.repository.RedisKeyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 订阅其他实例发出的 Creative Event 唤醒信号。
 */
@Configuration
@ConditionalOnProperty(prefix = "manga.creative", name = "redis-listener-enabled",
        havingValue = "true", matchIfMissing = true)
public class CreativeEventRedisListener {

    /** 创建按 Creative Run 频道模式订阅的 Redis 容器。 */
    @Bean(destroyMethod = "stop")
    @Lazy
    public RedisMessageListenerContainer creativeRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            CreativeEventSignalHub signalHub,
            RedisKeyFactory redisKeyFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, ignoredPattern) -> {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            redisKeyFactory.creativeRunIdFromEventChannel(channel).ifPresent(signalHub::signal);
        }, new PatternTopic(redisKeyFactory.creativeRunEventChannelPattern()));
        return container;
    }

    /** Redis 不可用时保持数据库轮询可用，并在后续周期重试订阅。 */
    @Component
    @Slf4j
    @ConditionalOnProperty(prefix = "manga.creative", name = "redis-listener-enabled",
            havingValue = "true", matchIfMissing = true)
    static class SubscriptionStarter {

        private final ObjectProvider<RedisMessageListenerContainer> containerProvider;

        SubscriptionStarter(ObjectProvider<RedisMessageListenerContainer> creativeRedisMessageListenerContainer) {
            this.containerProvider = creativeRedisMessageListenerContainer;
        }

        /** 尝试启动延迟创建的 Redis 订阅容器。 */
        @Scheduled(fixedDelayString = "${manga.creative.redis-listener-retry-interval:5s}")
        void ensureStarted() {
            try {
                RedisMessageListenerContainer container = containerProvider.getIfAvailable();
                if (Objects.isNull(container) || container.isRunning()) {
                    return;
                }
                container.start();
            } catch (RuntimeException exception) {
                log.debug("Creative event Redis wake-up is unavailable; database polling remains active");
            }
        }
    }
}
