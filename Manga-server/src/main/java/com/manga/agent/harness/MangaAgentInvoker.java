package com.manga.agent.harness;

import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.config.AgentHarnessProperties;
import com.manga.common.constant.AsyncConstants;
import io.agentscope.core.event.AgentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * AgentScope 唯一调用入口，统一执行会话串行、状态预检和两层资源释放。
 */
@Component
@Slf4j
public class MangaAgentInvoker {

    private final SessionSerialGate sessionSerialGate;
    private final AgentStateStorePreflight stateStorePreflight;
    private final AgentKernelPool kernelPool;
    private final TaskScheduler taskScheduler;
    private final AgentHarnessProperties properties;

    public MangaAgentInvoker(
            SessionSerialGate sessionSerialGate,
            AgentStateStorePreflight stateStorePreflight,
            AgentKernelPool kernelPool,
            @Qualifier(AsyncConstants.MANGA_COMMON_TASK_SCHEDULER) TaskScheduler taskScheduler,
            AgentHarnessProperties properties) {
        this.sessionSerialGate = sessionSerialGate;
        this.stateStorePreflight = stateStorePreflight;
        this.kernelPool = kernelPool;
        this.taskScheduler = taskScheduler;
        this.properties = properties;
    }

    /**
     * 订阅时依次获取状态会话闸门、验证 StateStore、固定 Kernel 并打开 Harness。
     * 正常、失败和取消都会按 Heartbeat → HarnessLease → KernelHandle → GateLease 逆序释放。
     */
    public Flux<AgentEvent> invoke(
            AgentBlueprint blueprint,
            AgentRuntimeContext context,
            String instruction) {
        return Flux.defer(() -> {
            InvocationResources resources = new InvocationResources();
            try {
                resources.gateLease = sessionSerialGate.acquire(context.stateSessionId());
                stateStorePreflight.verify(context);
                resources.kernelHandle = kernelPool.acquire(blueprint);
                resources.harnessLease = resources.kernelHandle.openHarness(context);
                resources.heartbeat = new SessionLeaseHeartbeat(
                        resources.gateLease,
                        properties.sessionLease(),
                        taskScheduler);
                return resources.harnessLease.stream(instruction)
                        .takeUntilOther(resources.heartbeat.lossSignal())
                        .doFinally(signalType -> resources.close());
            } catch (RuntimeException | Error exception) {
                resources.close();
                return Flux.error(exception);
            }
        });
    }

    /** 保存一次调用已获取的资源，并提供严格逆序幂等释放。 */
    private static final class InvocationResources implements AutoCloseable {

        private SessionGateLease gateLease;
        private KernelHandle kernelHandle;
        private HarnessLease harnessLease;
        private SessionLeaseHeartbeat heartbeat;

        /** 逆序关闭已成功获取的全部资源。 */
        @Override
        public void close() {
            closeSafely(heartbeat, "session heartbeat");
            closeSafely(harnessLease, "HarnessLease");
            closeSafely(kernelHandle, "KernelHandle");
            closeSafely(gateLease, "SessionGateLease");
        }

        /** 关闭单个资源，保留最先发生的业务失败而不让清理异常覆盖它。 */
        private void closeSafely(AutoCloseable closeable, String resourceName) {
            if (Objects.isNull(closeable)) {
                return;
            }
            try {
                closeable.close();
            } catch (Exception exception) {
                log.warn("Failed to close Agent invocation resource resource={}",
                        resourceName, exception);
            }
        }
    }
}
