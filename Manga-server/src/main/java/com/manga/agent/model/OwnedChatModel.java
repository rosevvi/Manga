package com.manga.agent.model;

import com.manga.agent.domain.CreativeRuntimeMessages;
import io.agentscope.core.model.Model;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 明确表示由 Manga Kernel 独占生命周期的 AgentScope 模型资源。
 */
public final class OwnedChatModel implements AutoCloseable {

    private final Model model;
    private final AtomicBoolean closed = new AtomicBoolean();

    public OwnedChatModel(Model model) {
        this.model = Objects.requireNonNull(model);
    }

    /** 返回供 Harness 使用的 AgentScope 模型。 */
    public Model model() {
        return model;
    }

    /** 幂等释放模型持有的网络资源。 */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true) || !(model instanceof AutoCloseable closeable)) {
            return;
        }
        try {
            closeable.close();
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(CreativeRuntimeMessages.AGENT_MODEL_CLOSE_FAILED, exception);
        }
    }
}
