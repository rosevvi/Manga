package com.manga.agent.generation;

import com.manga.common.exception.BusinessException;
import com.manga.common.enums.AiModelType;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.entity.CreativeArtifact;
import com.manga.entity.GenerationJob;
import com.manga.repository.GenerationJobRepository;
import com.manga.service.ProjectService;
import com.manga.service.MediaStorageService;
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
    private final MediaStorageService mediaStorageService;

    /** 下载时重新检查所有权并签署新地址，不沿用可能过期的历史预览链接。 */
    @Transactional(readOnly = true)
    public Download download(long userId, String jobId) {
        jobs.find(jobId).filter(job -> job.getUserId() == userId).orElseThrow(() -> new BusinessException(GenerationError.NOT_FOUND));
        CreativeArtifact artifact = jobs.artifact(jobId).orElseThrow(() -> new BusinessException(GenerationError.STATE));
        return new Download(mediaStorageService.downloadGeneratedMedia(artifact.getUrl()));
    }

    /** 临时下载能力，不能写入日志或长期缓存。 */
    public record Download(/** 平台本地地址或带附件响应头的短期 OSS 签名。 */ String url) {
        /** 避免签名能力进入日志。 */
        @Override public String toString() { return "Download[redacted]"; }
    }
    /** 批量查询产物，禁止大图片进入列表响应。 */
    @Transactional(readOnly = true)
    public List<GenerationJobView> list(long userId, long projectId, String runId, String conversationId, Long beforeId) {
        return list(userId, projectId, runId, conversationId, beforeId, null, null);
    }
    /** 先按认证范围、镜头和审批媒体类型过滤，再使用主键游标分页。 */
    @Transactional(readOnly = true)
    public List<GenerationJobView> list(long userId, long projectId, String runId, String conversationId,
            Long beforeId, Long shotId, AiModelType mediaType) {
        projects.requireAccessibleProject(projectId, userId);
        if (mediaType == AiModelType.TEXT) throw new BusinessException(GenerationError.PROTOCOL);
        String kind = Objects.isNull(mediaType) ? null : mediaType == AiModelType.VIDEO
                ? ChangeSetKind.VIDEO_GENERATION.name() : ChangeSetKind.IMAGE_GENERATION.name();
        List<GenerationJob> page = jobs.visible(userId, projectId, runId, conversationId, beforeId, GenerationConstants.PAGE_SIZE, shotId, kind);
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
                Objects.isNull(artifact) ? null : mediaStorageService.previewGeneratedImage(artifact.getUrl()),
                job.getCreatedAt(), job.getSubmittedAt(), job.getFinishedAt(),
                job.getUpdatedAt(), job.getReconciliationNote(), Objects.isNull(artifact) ? null : artifact.getAdoptedAt(),
                Objects.isNull(artifact) ? null : artifact.getAdoptedBy());
    }
}
