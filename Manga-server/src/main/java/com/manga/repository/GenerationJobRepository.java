package com.manga.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.manga.agent.generation.GenerationError;
import com.manga.agent.generation.SubmissionStatus;
import com.manga.agent.generation.GenerationProviderKey;
import com.manga.entity.CreativeArtifact;
import com.manga.entity.GenerationJob;
import com.manga.mapper.CreativeArtifactMapper;
import com.manga.mapper.GenerationJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/** 图片作业和产物的持久化边界，不在此执行外部调用。 */
@Repository
@RequiredArgsConstructor
public class GenerationJobRepository {
    private final GenerationJobMapper mapper;
    private final CreativeArtifactMapper artifacts;
    /** 读取安全元数据，领取执行权时才读取完整待转存媒体。 */
    public Optional<GenerationJob> find(String id) { return Optional.ofNullable(mapper.find(id)); }
    /** 调用者先持有 Run 锁。 */
    public GenerationJob lock(String id) { return mapper.lock(id); }
    /** 原子写入作业和唯一提交意图。 */
    public void create(GenerationJob job) {
        mapper.insert(job);
        mapper.createSubmission(job.getJobId(), job.getCreatedBy());
    }
    /** 围栏拒绝旧 worker 覆盖新代次。 */
    public void save(GenerationJob job, long expectedEpoch) {
        if (mapper.updateState(job, expectedEpoch) != 1) throw new IllegalStateException(GenerationError.STATE.message());
    }
    /** 返回待处理 ID，不读取媒体。 */
    public List<String> due(LocalDateTime now, int limit) { return mapper.findDue(now, limit); }
    /** 在 Run 锁内查询未终止作业。 */
    public List<String> byRun(String runId) { return mapper.findByRun(runId); }
    /** 查询权限范围内有界历史。 */
    public List<GenerationJob> visible(long userId, long projectId, String runId, String conversationId, Long beforeId, int limit) {
        return mapper.findVisible(userId, projectId, runId, conversationId, beforeId, limit);
    }
    /** 暴露窄接口以执行跨实例容量判断。 */
    public boolean capacity(GenerationJob job, int providerLimit, int userLimit) {
        mapper.lockCapacity(job.getProvider());
        return mapper.countInFlight(job.getProvider(), null) < providerLimit
                && mapper.countInFlight(job.getProvider(), job.getUserId()) < userLimit;
    }
    /** 更新已领取的唯一提交意图。 */
    public void submission(String jobId, SubmissionStatus status, String actor) { mapper.updateSubmission(jobId, status.name(), actor); }
    /** 单步骤只允许一个生成作业。 */
    public Optional<GenerationJob> byStep(String stepId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<GenerationJob>().eq(GenerationJob::getStepId, stepId)));
    }
    /** 按供应商事实标识查找作业，不接受客户端指定平台运行归属。 */
    public Optional<GenerationJob> byProviderId(GenerationProviderKey provider, String providerJobId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<GenerationJob>()
                .eq(GenerationJob::getProvider, provider).eq(GenerationJob::getProviderJobId, providerJobId)));
    }
    /** 列表批量读取产物，避免每个卡片单独访问数据库。 */
    public List<CreativeArtifact> artifacts(List<String> jobIds) {
        return jobIds.isEmpty() ? List.of() : artifacts.selectList(new LambdaQueryWrapper<CreativeArtifact>().in(CreativeArtifact::getJobId, jobIds));
    }
    /** 按作业查找唯一产物。 */
    public Optional<CreativeArtifact> artifact(String jobId) {
        return Optional.ofNullable(artifacts.selectOne(new LambdaQueryWrapper<CreativeArtifact>().eq(CreativeArtifact::getJobId, jobId)));
    }
    /** 首次发布转存产物，唯一键防止重复。 */
    public void createArtifact(CreativeArtifact value) { artifacts.insert(value); }
    /** 原子采用事务只修改审计字段，重复请求不推进项目版本。 */
    public void recordAdoption(CreativeArtifact value, long requestRevision, long appliedRevision, LocalDateTime at, long userId, String actor) {
        if (artifacts.recordAdoption(value.getId(), requestRevision, appliedRevision, at, userId, actor) != 1) {
            throw new IllegalStateException(GenerationError.STATE.message());
        }
    }
}

