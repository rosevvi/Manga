package com.manga.agent.generation;

import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeArtifact;
import com.manga.entity.GenerationJob;
import com.manga.repository.GenerationJobRepository;
import com.manga.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 图片作业快照用于 SSE 重连和页面刷新，查询始终限定认证用户与项目。 */
@Service
@RequiredArgsConstructor
public class GenerationJobQueryService {
    private final GenerationJobRepository jobs;
    private final ProjectService projects;
    /** 批量查询产物，禁止大图片进入列表响应。 */
    @Transactional(readOnly = true)
    public List<GenerationJobView> list(long userId, long projectId, String runId, String conversationId, Long beforeId) {
        projects.requireAccessibleProject(projectId, userId);
        List<GenerationJob> page = jobs.visible(userId, projectId, runId, conversationId, beforeId, GenerationConstants.PAGE_SIZE);
        Map<String, CreativeArtifact> artifacts = jobs.artifacts(page.stream().map(GenerationJob::getJobId).toList())
                .stream().collect(Collectors.toMap(CreativeArtifact::getJobId, Function.identity()));
        return page.stream().map(job -> view(job, artifacts.get(job.getJobId()))).toList();
    }
    /** 读取一个认证用户拥有的作业及平台产物。 */
    @Transactional(readOnly = true)
    public GenerationJobView find(long userId, String id) {
        GenerationJob job = jobs.find(id).filter(value -> value.getUserId() == userId)
                .orElseThrow(() -> new BusinessException(GenerationError.NOT_FOUND));
        return view(job, jobs.artifact(id).orElse(null));
    }
    /** 统一裁剪安全卡片字段。 */
    private GenerationJobView view(GenerationJob job, CreativeArtifact artifact) {
        return new GenerationJobView(job.getId(), job.getJobId(), job.getRunId(), job.getStepId(), job.getChangeSetId(),
                job.getProjectId(), job.getShotId(), job.getProvider(), job.getStatus(), job.getProgress(), Boolean.TRUE.equals(job.getCancelRequested()),
                job.getErrorCode(), job.getErrorMessage(), Objects.isNull(artifact) ? null : artifact.getId(),
                Objects.isNull(artifact) ? null : artifact.getUrl(), job.getCreatedAt(), job.getSubmittedAt(), job.getFinishedAt(),
                job.getUpdatedAt(), job.getReconciliationNote(), Objects.isNull(artifact) ? null : artifact.getAdoptedAt(),
                Objects.isNull(artifact) ? null : artifact.getAdoptedBy());
    }
}
