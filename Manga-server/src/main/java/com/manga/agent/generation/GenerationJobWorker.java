package com.manga.agent.generation;

import com.manga.common.constant.AsyncConstants;
import com.manga.entity.GenerationJob;
import com.manga.repository.GenerationJobRepository;
import com.manga.repository.GenerationCallbackRepository;
import com.manga.service.MediaStorageService;
import com.manga.service.storage.GeneratedMediaReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** 有界线程池执行外部 IO；调度只发唤醒信号，Job 表负责跨实例 claim 和恢复。 */
@Component
@Slf4j
public class GenerationJobWorker {
    private final GenerationJobService lifecycle;
    private final GenerationJobRepository jobs;
    private final GenerationProviderRegistry providers;
    private final GenerationProperties properties;
    private final MediaStorageService storage;
    private final Executor executor;
    private final GenerationCallbackRepository inbox;
    private final GenerationCallbackService callbacks;
    /** 使用 Manga 自定义线程池，不使用 CompletableFuture 默认池。 */
    public GenerationJobWorker(GenerationJobService lifecycle, GenerationJobRepository jobs,
            GenerationProviderRegistry providers, GenerationProperties properties, MediaStorageService storage,
            @Qualifier(AsyncConstants.MANGA_GENERATION_TASK_EXECUTOR) Executor executor,
            GenerationCallbackRepository inbox, GenerationCallbackService callbacks) {
        this.lifecycle = lifecycle; this.jobs = jobs; this.providers = providers;
        this.properties = properties; this.storage = storage; this.executor = executor;
        this.inbox = inbox; this.callbacks = callbacks;
    }
    /** 事务提交后立即调度；宕机或线程池拒绝时由扫描补偿。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void submitted(GenerationJobSignal signal) { dispatch(signal.jobId()); }
    /** 有界扫描，也恢复过期执行权和待转存结果。 */
    @Scheduled(fixedDelayString = GenerationConstants.INTERVAL)
    public void reconcile() {
        for (var callback : inbox.pending(properties.batchSize())) {
            try { callbacks.consume(callback); }
            catch (RuntimeException exception) { log.warn("Generation callback deferred inboxId={} exceptionType={}", callback.getId(), exception.getClass().getSimpleName()); }
        }
        for (String id : jobs.due(LocalDateTime.now(), properties.batchSize())) dispatch(id);
    }
    /** 仅在实际开始执行时领取 lease，排队时间不会消耗作业执行权。 */
    private void dispatch(String id) {
        try { executor.execute(() -> execute(id)); }
        catch (RejectedExecutionException exception) { log.debug("Generation dispatch deferred jobId={}", id); }
    }
    /** 每轮只完成一个阶段；媒体结果先落库再转存，绝不因存储故障重提生成。 */
    private void execute(String id) {
        GenerationJob job = null;
        try {
            job = lifecycle.claim(id);
            if (Objects.isNull(job)) return;
            GenerationProvider provider = providers.require(job.getProvider());
            if (job.getStatus() == GenerationJobStatus.SUBMITTING) lifecycle.accept(job, provider.submit(job));
            else if (job.getStatus() == GenerationJobStatus.MATERIALIZING) {
                if (Boolean.TRUE.equals(job.getCancelRequested())) {
                    lifecycle.accept(job, new GenerationSubmission(job.getProviderJobId(),
                            new GenerationSnapshot(GenerationJobStatus.CANCELLED, null, null, null)));
                    return;
                }
                byte[] data = decode(job.getResultBase64());
                GeneratedMediaReference reference;
                try { reference = storage.prepareGenerated(job.getJobId(), job.getOwnerEpoch()); }
                catch (RuntimeException exception) { throw new GenerationException(GenerationError.STORAGE); }
                var object = lifecycle.prepareMedia(job, reference);
                try { storage.storeGeneratedBytes(data, reference); }
                catch (RuntimeException exception) { throw new GenerationException(GenerationError.STORAGE); }
                lifecycle.materialized(job, object.getId(), data.length, digest(data));
            } else {
                if (Boolean.TRUE.equals(job.getCancelRequested())) provider.cancel(job);
                lifecycle.accept(job, new GenerationSubmission(job.getProviderJobId(), provider.query(job)));
            }
        } catch (GenerationException exception) {
            if (Objects.nonNull(job)) lifecycle.failed(job, exception.error());
        } catch (RuntimeException exception) {
            // 不输出供应商异常正文，避免密钥或完整媒体响应进入日志。
            log.warn("Generation worker interrupted jobId={} exceptionType={}", id, exception.getClass().getSimpleName());
            if (Objects.nonNull(job)) lifecycle.failed(job, job.getStatus() == GenerationJobStatus.SUBMITTING
                    ? GenerationError.SUBMISSION_UNKNOWN : GenerationError.TRANSIENT);
        }
    }
    /** 在解码和图片读取前校验长度、PNG 格式及像素上限，防止异常大对象。 */
    private byte[] decode(String base64) {
        if (Objects.isNull(base64) || base64.length() > ((properties.maxMediaBytes() + 2L) / 3) * 4) throw new GenerationException(GenerationError.PROTOCOL);
        byte[] data;
        try { data = Base64.getDecoder().decode(base64); }
        catch (IllegalArgumentException exception) { throw new GenerationException(GenerationError.PROTOCOL); }
        if (data.length == 0 || data.length > properties.maxMediaBytes()) throw new GenerationException(GenerationError.PROTOCOL);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> candidates = ImageIO.getImageReaders(input);
            if (!candidates.hasNext()) throw new GenerationException(GenerationError.PROTOCOL);
            ImageReader reader = candidates.next();
            try {
                reader.setInput(input);
                if (!GenerationConstants.FORMAT.equalsIgnoreCase(reader.getFormatName())
                        || reader.getWidth(0) < 1 || reader.getHeight(0) < 1
                        || reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) throw new GenerationException(GenerationError.PROTOCOL);
                var image = reader.read(0);
                if (Objects.isNull(image)) throw new GenerationException(GenerationError.PROTOCOL);
                image.flush();
            } finally { reader.dispose(); }
        } catch (IOException exception) { throw new GenerationException(GenerationError.PROTOCOL); }
        return data;
    }
    /** 平台产物按内容摘要追溯。 */
    private String digest(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(GenerationError.PROTOCOL.message(), exception); }
    }
}
