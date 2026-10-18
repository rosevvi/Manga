package com.manga.agent.execution;

import com.manga.common.constant.AsyncConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * 在受控业务线程池中领取并执行 Step，重复调度由原子领取自然去重。
 */
@Component
@Slf4j
public class CreativeStepDispatcher {

    private final CreativeStepClaimService claimService;
    private final CreativeStepExecutorRegistry executorRegistry;
    private final CreativeStepLifecycleService lifecycleService;
    private final Executor taskExecutor;

    /** 注入 Step 生命周期组件和 Manga 通用受控线程池。 */
    public CreativeStepDispatcher(CreativeStepClaimService claimService,
            CreativeStepExecutorRegistry executorRegistry,
            CreativeStepLifecycleService lifecycleService,
            @Qualifier(AsyncConstants.MANGA_COMMON_TASK_EXECUTOR) Executor taskExecutor) {
        this.claimService = claimService;
        this.executorRegistry = executorRegistry;
        this.lifecycleService = lifecycleService;
        this.taskExecutor = taskExecutor;
    }

    /** 非阻塞提交一次调度；事务提交回调和 Dispatch Outbox 共用此入口。 */
    public void dispatchAsync(String stepId, long scheduleVersion) {
        taskExecutor.execute(() -> dispatch(stepId, scheduleVersion));
    }

    /** 原子领取后路由到类型化执行器；未领取表示重复或过期信号。 */
    private void dispatch(String stepId, long scheduleVersion) {
        try {
            Optional<ClaimedCreativeStep> claimed = claimService.claim(stepId, scheduleVersion);
            if (claimed.isEmpty()) {
                return;
            }
            ClaimedCreativeStep step = claimed.get();
            executorRegistry.require(step.stepType()).execute(step);
        } catch (RuntimeException exception) {
            log.error("Creative Step dispatch or execution failed stepId={} scheduleVersion={}",
                    stepId, scheduleVersion, exception);
            claimService.findOwnedClaim(stepId)
                    .ifPresent(step -> lifecycleService.fail(step, exception));
        }
    }
}
