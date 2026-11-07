package com.manga.agent.generation;
import com.manga.agent.application.CreativeEventService;
import com.manga.agent.domain.CreativeActor;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.event.CreativeEventSources;
import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeArtifact;
import com.manga.entity.GenerationJob;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.GenerationJobRepository;
import com.manga.service.StoryboardShotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;
/** 产物采用与生成解耦，领域写入、项目版本、采用人和来源事件原子提交。 */
@Service
@RequiredArgsConstructor
public class ArtifactApplicationService {
    private final GenerationJobRepository jobs;
    private final CreativeRunRepository runs;
    private final StoryboardShotService shots;
    private final CreativeEventService events;
    /** 展示当前镜头图片，生成时的旧版本不能直接作为采用许可。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ArtifactAdoptionPreview preview(String id, long userId) {
        GenerationJob job = requireJob(id, userId);
        CreativeArtifact artifact = requireArtifact(job);
        return new ArtifactAdoptionPreview(id, artifact.getId(), artifact.getUrl(), shots.imageSnapshot(job.getProjectId(), job.getShotId(), userId));
    }
    /** Run→Job→Project 固定锁顺序；同一预览请求重放返回原采用结果，不重新覆盖人工修改。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ArtifactAdoptionResult adopt(String id, long userId, long revision) {
        GenerationJob candidate = requireJob(id, userId);
        runs.lockByRunId(candidate.getRunId()); GenerationJob job = jobs.lock(id);
        CreativeArtifact artifact = requireArtifact(job);
        if (Objects.equals(artifact.getAdoptionRequestRevision(), revision) && Objects.nonNull(artifact.getAdoptedAt())) {
            return new ArtifactAdoptionResult(artifact.getId(), job.getShotId(), revision, artifact.getAdoptedProjectRevision(), artifact.getAdoptedAt(), artifact.getAdoptedBy());
        }
        String actor = CreativeActor.user(userId);
        long applied = shots.adoptGeneratedImage(job.getProjectId(), job.getShotId(), userId, revision, artifact.getId(), artifact.getUrl(), actor);
        LocalDateTime at = LocalDateTime.now();
        jobs.recordAdoption(artifact, revision, applied, at, userId, actor);
        ArtifactAdoptionResult result = new ArtifactAdoptionResult(artifact.getId(), job.getShotId(), revision, applied, at, userId);
        events.append(job.getRunId(), job.getStepId(), CreativeEventType.ARTIFACT_ADOPTED, CreativeEventSources.RUNTIME, job.getJobId(), result);
        return result;
    }
    /** 归属检查使用认证用户，禁止跨项目选择产物。 */
    private GenerationJob requireJob(String id, long userId) {
        return jobs.find(id).filter(job -> job.getUserId() == userId)
                .orElseThrow(() -> new BusinessException(GenerationError.NOT_FOUND, HttpStatus.NOT_FOUND));
    }
    /** 只有已经转存的成功产物可以采用，取消和未知作业都不提供采用入口。 */
    private CreativeArtifact requireArtifact(GenerationJob job) {
        if (job.getStatus() != GenerationJobStatus.SUCCEEDED) throw new BusinessException(GenerationError.STATE, HttpStatus.CONFLICT);
        return jobs.artifact(job.getJobId()).orElseThrow(() -> new BusinessException(GenerationError.NOT_FOUND, HttpStatus.NOT_FOUND));
    }
}
