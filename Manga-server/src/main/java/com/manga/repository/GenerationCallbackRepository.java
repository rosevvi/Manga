package com.manga.repository;
import com.manga.agent.generation.GenerationCallback;
import com.manga.agent.generation.CallbackInboxStatus;
import com.manga.agent.generation.GenerationError;
import com.manga.agent.generation.GenerationProviderKey;
import com.manga.common.exception.BusinessException;
import com.manga.entity.GenerationCallbackInbox;
import com.manga.mapper.GenerationCallbackMapper;
import com.manga.mapper.GenerationCallbackMapper.GenerationCallbackEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.time.LocalDateTime;
/** 回调去重和消费状态持久化边界。 */
@Repository
@RequiredArgsConstructor
public class GenerationCallbackRepository {
    private final GenerationCallbackMapper mapper;
    /** 重复接收不覆盖正文摘要，同 ID 不同正文直接拒绝。 */
    public void receive(GenerationProviderKey provider, GenerationCallback callback, String hash, String actor) {
        mapper.receive(provider, new GenerationCallbackEvent(callback.eventId(), callback.providerJobId(), hash), actor);
        if (!hash.equals(mapper.payloadHash(provider, callback.eventId()))) throw new BusinessException(GenerationError.CALLBACK);
    }
    /** 有界读取待处理事实引用。 */
    public List<GenerationCallbackInbox> pending(int limit) { return mapper.pending(LocalDateTime.now(), limit); }
    /** 尚未绑定的回调按时间重试，不持续占据批次队首。 */
    public void defer(long id, LocalDateTime nextAttemptAt, String actor) { mapper.defer(id, LocalDateTime.now(), nextAttemptAt, actor); }
    /** 只有首次消费能够唤醒作业；过期和终态提示直接忽略。 */
    public boolean complete(long id, boolean ignored, String actor) { return mapper.complete(id, ignored ? CallbackInboxStatus.IGNORED : CallbackInboxStatus.PROCESSED, actor) == 1; }
}
