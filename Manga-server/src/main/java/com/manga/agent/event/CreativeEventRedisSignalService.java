package com.manga.agent.event;

import com.manga.repository.RedisKeyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 通过每个 Creative Run 独立的 Redis Pub/Sub 频道发布和订阅轻量唤醒提示。
 *
 * <p>Redis 消息只携带已经提交的事件序号；MySQL Event Journal 始终是事件正文的唯一事实源。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreativeEventRedisSignalService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisKeyFactory redisKeyFactory;

    /** 发布一条已提交事件的序号，用于立即唤醒正在订阅该 Run 的 SSE。 */
    public Mono<Void> publishWakeup(String runId, long sequence) {
        String safeRunId = requireRunId(runId);
        if (sequence <= 0) {
            return Mono.error(new IllegalArgumentException("sequence must be positive"));
        }
        return redisTemplate.convertAndSend(
                        redisKeyFactory.creativeRunEventChannel(safeRunId),
                        Long.toString(sequence))
                .then();
    }

    /**
     * 在 Redis 确认频道订阅已经生效后返回消息流，避免“先回放、后订阅”造成的竞态窗口。
     */
    public Mono<Flux<Long>> wakeupsWhenSubscribed(String runId) {
        String safeRunId = requireRunId(runId);
        return redisTemplate
                .listenToChannelLater(redisKeyFactory.creativeRunEventChannel(safeRunId))
                .map(messages -> Flux.from(messages)
                        .handle((message, sink) -> {
                            String payload = message.getMessage();
                            try {
                                long sequence = Long.parseLong(payload);
                                if (sequence > 0) {
                                    sink.next(sequence);
                                } else {
                                    log.warn("Ignoring non-positive Creative event wake-up runId={}", safeRunId);
                                }
                            } catch (NumberFormatException exception) {
                                log.warn("Ignoring malformed Creative event wake-up runId={}", safeRunId);
                            }
                        }));
    }

    /** 限制频道动态部分，避免非法或过长 Run 标识污染 Redis key 空间。 */
    private String requireRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        if (runId.length() > 64 || !StandardCharsets.US_ASCII.newEncoder().canEncode(runId)) {
            throw new IllegalArgumentException("runId must be at most 64 ASCII characters");
        }
        return runId;
    }
}
