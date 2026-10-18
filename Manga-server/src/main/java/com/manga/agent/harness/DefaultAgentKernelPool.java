package com.manga.agent.harness;

import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentKernelKey;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.config.AgentHarnessProperties;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeRuntimeMessages;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用有界 LRU 和引用计数实现进程内 Agent Kernel 缓存。
 */
@Component
public class DefaultAgentKernelPool implements AgentKernelPool {

    private final Object monitor = new Object();
    private final Map<AgentKernelKey, KernelEntry> entries = new HashMap<>();
    private final MangaHarnessFactory harnessFactory;
    private final int maxKernels;
    private final Duration acquireTimeout;
    private final Duration shutdownDrainTimeout;
    private boolean draining;

    public DefaultAgentKernelPool(
            MangaHarnessFactory harnessFactory,
            AgentHarnessProperties properties) {
        this.harnessFactory = harnessFactory;
        this.maxKernels = properties.maxKernels();
        this.acquireTimeout = properties.kernelAcquireTimeout();
        this.shutdownDrainTimeout = properties.shutdownDrainTimeout();
    }

    /** 获取共享 Kernel；只淘汰没有活跃 Handle 的最近最少使用项。 */
    @Override
    public KernelHandle acquire(AgentBlueprint blueprint) {
        Objects.requireNonNull(blueprint);
        long deadline = System.nanoTime() + acquireTimeout.toNanos();
        synchronized (monitor) {
            while (true) {
                if (draining) {
                    throw capacity(CreativeRuntimeMessages.AGENT_KERNEL_POOL_DRAINING);
                }
                KernelEntry existing = entries.get(blueprint.kernelKey());
                if (Objects.nonNull(existing)) {
                    return retain(existing);
                }
                evictIdleIfAtCapacity();
                if (entries.size() < maxKernels) {
                    AgentKernel kernel = harnessFactory.createKernel(blueprint);
                    KernelEntry created = new KernelEntry(kernel);
                    entries.put(kernel.key(), created);
                    return retain(created);
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    throw capacity(CreativeRuntimeMessages.AGENT_KERNEL_CAPACITY_EXHAUSTED);
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw capacity(CreativeRuntimeMessages.AGENT_KERNEL_CAPACITY_EXHAUSTED);
                }
            }
        }
    }

    /** 进入排空状态，等待引用归零或超时，然后关闭全部 Kernel。 */
    @Override
    public void drain() {
        long deadline = System.nanoTime() + shutdownDrainTimeout.toNanos();
        synchronized (monitor) {
            draining = true;
            while (hasActiveHandles()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    break;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            entries.values().forEach(entry -> entry.kernel.close());
            entries.clear();
        }
    }

    /** Spring 容器关闭时排空 KernelPool。 */
    @PreDestroy
    public void closeOnShutdown() {
        drain();
    }

    /** 增加 Kernel 引用并返回独立幂等 Handle。 */
    private KernelHandle retain(KernelEntry entry) {
        entry.references++;
        entry.lastAccessNanos = System.nanoTime();
        return new PooledKernelHandle(entry);
    }

    /** 容量已满时淘汰无引用的 LRU Kernel。 */
    private void evictIdleIfAtCapacity() {
        if (entries.size() < maxKernels) {
            return;
        }
        entries.entrySet().stream()
                .filter(candidate -> candidate.getValue().references == 0)
                .min(Comparator.comparingLong(candidate -> candidate.getValue().lastAccessNanos))
                .ifPresent(candidate -> {
                    entries.remove(candidate.getKey());
                    candidate.getValue().kernel.close();
                });
    }

    /** 判断当前是否仍有活跃 Handle。 */
    private boolean hasActiveHandles() {
        return entries.values().stream().anyMatch(entry -> entry.references > 0);
    }

    /** 创建统一的容量或关闭状态异常。 */
    private AgentRuntimeException capacity(String message) {
        return new AgentRuntimeException(CreativeErrorCode.KERNEL_CAPACITY_EXHAUSTED, message);
    }

    /** 缓存项只保存 Kernel 引用计数和 LRU 时间，不保存请求上下文。 */
    private static final class KernelEntry {

        private final AgentKernel kernel;
        private int references;
        private long lastAccessNanos = System.nanoTime();

        private KernelEntry(AgentKernel kernel) {
            this.kernel = kernel;
        }
    }

    /** 固定一个缓存项生命周期的幂等 Handle。 */
    private final class PooledKernelHandle implements KernelHandle {

        private final KernelEntry entry;
        private final AtomicBoolean closed = new AtomicBoolean();

        private PooledKernelHandle(KernelEntry entry) {
            this.entry = entry;
        }

        /** 创建请求隔离 Harness，已释放 Handle 不可再次使用。 */
        @Override
        public HarnessLease openHarness(AgentRuntimeContext context) {
            if (closed.get()) {
                throw new IllegalStateException(CreativeRuntimeMessages.AGENT_KERNEL_HANDLE_CLOSED);
            }
            return entry.kernel.openHarness(context);
        }

        /** 释放 Kernel 引用并唤醒等待容量的请求。 */
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            synchronized (monitor) {
                entry.references--;
                entry.lastAccessNanos = System.nanoTime();
                monitor.notifyAll();
            }
        }
    }
}
