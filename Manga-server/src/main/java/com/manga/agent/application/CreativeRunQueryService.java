package com.manga.agent.application;

import com.manga.agent.api.CreativeRunResponse;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提供不依赖具体执行引擎的 Creative Run 所有权查询和响应映射。
 */
@Service
@RequiredArgsConstructor
public class CreativeRunQueryService {

    private final CreativeRunRepository runRepository;

    /** 查询当前用户拥有的 Run，并返回稳定 API 快照。 */
    @Transactional(readOnly = true)
    public CreativeRunResponse findOwned(String runId, long userId) {
        CreativeRun run = runRepository.findOwned(runId, userId)
                .orElseThrow(() -> new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN));
        return toResponse(run);
    }

    /** 将持久化实体投影为 Creative Run API 契约。 */
    private CreativeRunResponse toResponse(CreativeRun run) {
        return new CreativeRunResponse(
                run.getRunId(),
                run.getRequestId(),
                run.getConversationId(),
                run.getProjectId(),
                run.getTriggerSource(),
                run.getOperationType(),
                run.getTargetType(),
                run.getTargetId(),
                run.getStatus(),
                run.getPermissionPolicy(),
                run.getEngineVersion(),
                run.getWorkflowVersion(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getErrorCode(),
                run.getErrorMessage()
        );
    }
}
