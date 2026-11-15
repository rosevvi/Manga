package com.manga.agent.application;

import com.manga.agent.api.CreativeRuntimeResponseCode;
import com.manga.agent.config.CreativeMemoryProperties;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.repository.CreativeConversationRepository;
import com.manga.repository.CreativeRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.Objects;

/** 新对话受理之前补齐已结束轮次，避免在持有会话锁时等待投影而形成反向锁依赖。 */
@Service
@RequiredArgsConstructor
public class CreativeProjectionBarrier {
    private final CreativeConversationRepository conversations;
    private final CreativeRunRepository runs;
    private final CreativeMessageProjector projector;
    private final CreativeMemoryProperties properties;

    /** 非事务门面逐批提交历史投影；活动对话仍由统一冲突契约拒绝。 */
    public void beforeStart(long userId, String conversationId) {
        if (Objects.isNull(conversationId)) { return; }
        conversations.findOwned(conversationId, userId).orElseThrow(() ->
                new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN));
        if (runs.findActiveConversation(conversationId).filter(run -> run.getStatus() != CreativeRunStatus.CANCEL_REQUESTED).isPresent()) {
            throw new BusinessException(CreativeRuntimeResponseCode.ACTIVE_RUN_CONFLICT, HttpStatus.CONFLICT);
        }
        for (String runId : runs.findUnprojected(conversationId, properties.projectionBatchSize())) {
            while (projector.project(runId) > 0) { /* 每批事务提交，事实不重新生成。 */ }
        }
        if (!runs.findUnprojected(conversationId, 1).isEmpty()) {
            throw new BusinessException(CreativeRuntimeResponseCode.PROJECTION_BUSY, HttpStatus.CONFLICT);
        }
    }
}
