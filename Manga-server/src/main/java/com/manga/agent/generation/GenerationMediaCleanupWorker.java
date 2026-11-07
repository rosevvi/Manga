package com.manga.agent.generation;
import com.manga.common.constant.AsyncConstants;
import com.manga.repository.GenerationMediaObjectRepository;
import com.manga.service.MediaStorageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
/** 使用媒体线程池有界回收，不在调度器线程或数据库事务中删除文件。 */
@Component
@Slf4j
public class GenerationMediaCleanupWorker {
    private final GenerationMediaObjectRepository media;
    private final GenerationMediaCleanupService lifecycle;
    private final GenerationCleanupProperties properties;
    private final MediaStorageService storage;
    private final Executor executor;
    /** 复用已隔离的媒体 IO 线程池，队满时保留数据库责任。 */
    public GenerationMediaCleanupWorker(GenerationMediaObjectRepository media, GenerationMediaCleanupService lifecycle,
            GenerationCleanupProperties properties, MediaStorageService storage,
            @Qualifier(AsyncConstants.MANGA_GENERATION_TASK_EXECUTOR) Executor executor) {
        this.media=media; this.lifecycle=lifecycle; this.properties=properties; this.storage=storage; this.executor=executor;
    }
    /** 定期扫描到期登记对象，默认等待 24 小时后才进入回收。 */
    @Scheduled(fixedDelayString = GenerationConstants.CLEANUP_INTERVAL)
    public void reconcile() {
        for (long id : media.due(LocalDateTime.now(), properties.batchSize())) {
            try { executor.execute(() -> remove(id)); }
            catch (RejectedExecutionException exception) { log.debug("Generation cleanup deferred objectId={}", id); }
        }
    }
    /** 不使用用户 URL 删除，失败保持 RETIRING 并退避，已发布对象不会领取成功。 */
    private void remove(long id) {
        var object = lifecycle.claim(id);
        if (Objects.isNull(object)) return;
        boolean deleted = false;
        try { storage.deleteGenerated(object.reference()); deleted = true; }
        catch (RuntimeException exception) { log.warn("Generation media cleanup failed objectId={} storageType={} exceptionType={}", id, object.getStorageType(), exception.getClass().getSimpleName()); }
        lifecycle.complete(object, deleted);
    }
}

