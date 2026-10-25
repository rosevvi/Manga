package com.manga.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.config.CreativeRuntimeProperties;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.event.CreativeEventRedisSignalService;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.entity.CreativeEvent;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeEventRepository;
import com.manga.repository.CreativeRunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证事件事实提交和 Redis 唤醒之间的事务边界。 */
class CreativeEventServiceTests {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldPublishRedisWakeupOnlyAfterTransactionCommit() {
        CreativeRunRepository runRepository = mock(CreativeRunRepository.class);
        CreativeEventRepository eventRepository = mock(CreativeEventRepository.class);
        CreativeEventRedisSignalService signals = mock(CreativeEventRedisSignalService.class);
        CreativeRun run = CreativeRun.builder().runId("run-test").nextSequence(7L).build();
        when(runRepository.lockByRunId("run-test")).thenReturn(run);
        when(eventRepository.create(any(CreativeEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(signals.publishWakeup("run-test", 7L)).thenReturn(Mono.empty());
        CreativeEventService service = new CreativeEventService(
                runRepository,
                eventRepository,
                signals,
                new ObjectMapper(),
                new CreativeRuntimeProperties("durable-v1", "v1", 1, Duration.ofMinutes(30)),
                mock(CreativeMessageProjector.class),
                mock(AgentStateSessionService.class));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.append("run-test", CreativeEventType.RUN_ACCEPTED, Map.of("accepted", true));

        verify(signals, never()).publishWakeup("run-test", 7L);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(signals).publishWakeup("run-test", 7L);
        verify(runRepository).update(run);
    }
}
