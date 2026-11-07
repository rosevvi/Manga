package com.manga.agent.generation;
import com.manga.agent.domain.SystemActor;
import com.manga.entity.GenerationJob;
import com.manga.entity.GenerationMediaObject;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.GenerationJobRepository;
import com.manga.repository.GenerationMediaObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
/** 原子撤销未发布对象的发布权；存储删除由事务外 worker 执行。 */
@Service
@RequiredArgsConstructor
public class GenerationMediaCleanupService {
    private final GenerationMediaObjectRepository media;
    private final GenerationJobRepository jobs;
    private final CreativeRunRepository runs;
    private final GenerationCleanupProperties properties;
    /** 多实例先复核引用和有效 worker，再以独立令牌领取回收权。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GenerationMediaObject claim(long id) {
        GenerationMediaObject candidate = media.find(id).orElse(null);
        if (Objects.isNull(candidate)) return null;
        GenerationJob reference = jobs.find(candidate.getJobId()).orElse(null);
        if (Objects.isNull(reference)) return null;
        runs.lockByRunId(reference.getRunId());
        GenerationJob job = jobs.lock(reference.getJobId());
        GenerationMediaObject object = media.lock(id);
        LocalDateTime now = LocalDateTime.now();
        if (object.getStatus() == GenerationMediaStatus.PUBLISHED || object.getNextAttemptAt().isAfter(now)
                || Objects.nonNull(object.getClaimUntil()) && object.getClaimUntil().isAfter(now)) return null;
        if (media.referenced(id)) { media.publish(id, actor()); return null; }
        boolean activeWriter = Objects.equals(job.getOwnerEpoch(), object.getOwnerEpoch())
                && job.getStatus() == GenerationJobStatus.MATERIALIZING
                && Objects.nonNull(job.getLeaseUntil()) && job.getLeaseUntil().isAfter(now);
        if (activeWriter) { media.defer(id, now.plus(properties.retryDelay()), actor()); return null; }
        String token = UUID.randomUUID().toString();
        LocalDateTime until = now.plus(properties.lease());
        media.claim(id, token, until, actor());
        object.setClaimOwner(token); object.setClaimUntil(until); object.setStatus(GenerationMediaStatus.RETIRING);
        return object;
    }
    /** 删除结果只更新领取者的对象，失败会保留回收责任，删除墓碑定期检查晚到写入。 */
    @Transactional
    public void complete(GenerationMediaObject object, boolean deleted) {
        media.finish(object.getId(), object.getClaimOwner(), deleted, LocalDateTime.now().plus(
                deleted ? properties.tombstoneInterval() : properties.retryDelay()), actor());
    }
    /** 系统回收的稳定审计身份。 */
    private String actor() { return SystemActor.CREATIVE_RUNTIME.code(); }
}

