package com.manga.agent.harness;

import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentKernelKey;
import com.manga.agent.config.AgentHarnessProperties;
import com.manga.agent.domain.CreativeErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 KernelPool 的容量等待、LRU 淘汰和资源关闭语义。
 */
class DefaultAgentKernelPoolTests {

    @Test
    void shouldRejectNewKernelWhileOnlyEntryHasActiveHandle() {
        MangaHarnessFactory factory = mock(MangaHarnessFactory.class);
        AgentBlueprint firstBlueprint = blueprint("kernel-a");
        AgentBlueprint secondBlueprint = blueprint("kernel-b");
        AgentKernel firstKernel = kernel("kernel-a");
        when(factory.createKernel(firstBlueprint)).thenReturn(firstKernel);
        DefaultAgentKernelPool pool = new DefaultAgentKernelPool(
                factory,
                properties(Duration.ofMillis(10)));
        KernelHandle activeHandle = pool.acquire(firstBlueprint);

        assertThatThrownBy(() -> pool.acquire(secondBlueprint))
                .isInstanceOfSatisfying(AgentRuntimeException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(CreativeErrorCode.KERNEL_CAPACITY_EXHAUSTED));

        activeHandle.close();
        pool.drain();
    }

    @Test
    void shouldEvictLeastRecentlyUsedIdleKernel() {
        MangaHarnessFactory factory = mock(MangaHarnessFactory.class);
        AgentBlueprint firstBlueprint = blueprint("kernel-a");
        AgentBlueprint secondBlueprint = blueprint("kernel-b");
        AgentKernel firstKernel = kernel("kernel-a");
        AgentKernel secondKernel = kernel("kernel-b");
        when(factory.createKernel(firstBlueprint)).thenReturn(firstKernel);
        when(factory.createKernel(secondBlueprint)).thenReturn(secondKernel);
        DefaultAgentKernelPool pool = new DefaultAgentKernelPool(
                factory,
                properties(Duration.ofMillis(50)));

        pool.acquire(firstBlueprint).close();
        KernelHandle secondHandle = pool.acquire(secondBlueprint);

        verify(firstKernel).close();
        secondHandle.close();
        pool.drain();
        verify(secondKernel).close();
    }

    private AgentHarnessProperties properties(Duration acquireTimeout) {
        return new AgentHarnessProperties(
                Duration.ofSeconds(30),
                1,
                acquireTimeout,
                Duration.ofMillis(100));
    }

    private AgentBlueprint blueprint(String key) {
        AgentBlueprint blueprint = mock(AgentBlueprint.class);
        when(blueprint.kernelKey()).thenReturn(new AgentKernelKey(key));
        return blueprint;
    }

    private AgentKernel kernel(String key) {
        AgentKernel kernel = mock(AgentKernel.class);
        when(kernel.key()).thenReturn(new AgentKernelKey(key));
        return kernel;
    }
}
