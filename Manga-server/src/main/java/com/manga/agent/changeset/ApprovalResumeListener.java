package com.manga.agent.changeset;

import com.manga.agent.execution.CreativeDispatchSignal;
import com.manga.agent.execution.CreativeStepDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 审批事务提交后立即恢复；投递失败由持久化 Outbox 补偿。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalResumeListener {
    private final CreativeStepDispatcher dispatcher;

    /** 不在审批事务内获取 Harness，也不让线程池拒绝掩盖已成功的审批。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void resume(CreativeDispatchSignal signal) {
        try { dispatcher.dispatchAsync(signal.stepId(), signal.scheduleVersion()); }
        catch (RuntimeException failure) { log.warn("Approval resume deferred to outbox stepId={}", signal.stepId(), failure); }
    }
}
