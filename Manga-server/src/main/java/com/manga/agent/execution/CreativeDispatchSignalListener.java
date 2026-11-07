package com.manga.agent.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 审批、外部作业和协作图共用的提交后调度入口，投递失败由持久化 Outbox 补偿。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreativeDispatchSignalListener {
    private final CreativeStepDispatcher dispatcher;

    /** 事务提交后投递，避免获取 Harness 时仍持有业务事务及数据库行锁。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(CreativeDispatchSignal signal) {
        try { dispatcher.dispatchAsync(signal.stepId(), signal.scheduleVersion()); }
        catch (RuntimeException failure) { log.warn("Creative dispatch deferred to outbox stepId={}", signal.stepId(), failure); }
    }
}
