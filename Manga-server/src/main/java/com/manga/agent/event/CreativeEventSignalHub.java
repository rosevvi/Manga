package com.manga.agent.event;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 汇总进程内和 Redis 唤醒信号，事件内容始终从数据库读取。
 */
@Component
public class CreativeEventSignalHub {

    private final ConcurrentMap<String, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();

    /** 订阅指定 Run 的轻量事件唤醒信号。 */
    public Flux<String> listen(String runId) {
        return sinks.computeIfAbsent(runId, ignored -> Sinks.many().multicast().directBestEffort()).asFlux();
    }

    /** 唤醒当前实例内指定 Run 的订阅者。 */
    public void signal(String runId) {
        Sinks.Many<String> sink = sinks.get(runId);
        if (Objects.nonNull(sink)) {
            sink.tryEmitNext(runId);
        }
    }
}
