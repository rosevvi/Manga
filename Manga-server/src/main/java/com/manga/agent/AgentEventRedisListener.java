package com.manga.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** 订阅所有实例发出的事件唤醒信号。 */
@Configuration
@ConditionalOnProperty(prefix = "manga.agent", name = "redis-listener-enabled", havingValue = "true", matchIfMissing = true)
public class AgentEventRedisListener {

    @Bean(destroyMethod = "stop")
    @Lazy
    public RedisMessageListenerContainer mangaAgentRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory, AgentEventSignalHub signalHub) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, ignoredPattern) -> {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String prefix = "manga:agent:run:";
            if (channel.startsWith(prefix)) {
                signalHub.signal(channel.substring(prefix.length()));
            }
        }, new PatternTopic("manga:agent:run:*"));
        return container;
    }

    /** Redis 是实时唤醒优化，不应因首次不可用阻止数据库事件流启动。 */
    @Component
    @Slf4j
    @ConditionalOnProperty(prefix = "manga.agent", name = "redis-listener-enabled", havingValue = "true", matchIfMissing = true)
    static class SubscriptionStarter {
        private final ObjectProvider<RedisMessageListenerContainer> containerProvider;

        SubscriptionStarter(ObjectProvider<RedisMessageListenerContainer> mangaAgentRedisMessageListenerContainer) {
            this.containerProvider = mangaAgentRedisMessageListenerContainer;
        }

        @Scheduled(fixedDelay = 5000)
        void ensureStarted() {
            try {
                RedisMessageListenerContainer container = containerProvider.getIfAvailable();
                if (container == null || container.isRunning()) {
                    return;
                }
                container.start();
            } catch (RuntimeException exception) {
                log.debug("Agent Redis wake-up subscription is unavailable; database polling remains active");
            }
        }
    }
}
