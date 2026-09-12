package com.manga.agent;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 汇总 Redis 唤醒信号；事件内容始终从 MySQL 日志读取。 */
@Component
public class AgentEventSignalHub {

    private final ConcurrentMap<String, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();

    public Flux<String> listen(String runId) {
        return sinks.computeIfAbsent(runId, ignored -> Sinks.many().multicast().directBestEffort()).asFlux();
    }

    public void signal(String runId) {
        Sinks.Many<String> sink = sinks.get(runId);
        if (sink != null) {
            sink.tryEmitNext(runId);
        }
    }
}
