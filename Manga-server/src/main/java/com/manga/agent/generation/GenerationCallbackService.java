package com.manga.agent.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.domain.SystemActor;
import com.manga.common.exception.BusinessException;
import com.manga.entity.GenerationCallbackInbox;
import com.manga.entity.GenerationJob;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.GenerationCallbackRepository;
import com.manga.repository.GenerationJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

/** 平台异步中继回调；验签、去重和乱序处理独立于供应商终态判定。 */
@Service
@RequiredArgsConstructor
public class GenerationCallbackService {
    /** 签名算法与摘要算法。 */
    private static final String HMAC = "HmacSHA256";
    /** 原始回调正文摘要算法。 */
    private static final String DIGEST = "SHA-256";
    private final GenerationProperties properties;
    private final GenerationCallbackRepository inbox;
    private final GenerationJobRepository jobs;
    private final CreativeRunRepository runs;
    private final ChangeSetCodec codec;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher publisher;

    /** 必须先校验原始字节签名再解析 JSON；此平台协议目前只供 Fake 验证，OpenAI Images 没有此回调。 */
    @Transactional
    public void receive(GenerationProviderKey provider, long timestamp, String signature, byte[] body) {
        long now = Instant.now().getEpochSecond();
        long skew = properties.callbackClockSkew().toSeconds();
        if (provider != GenerationProviderKey.FAKE || properties.callbackSecret().isBlank()
                || body.length > GenerationConstants.CALLBACK_MAX_BYTES || signature.length() != 64
                || timestamp < now - skew || timestamp > now + skew) reject();
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(properties.callbackSecret().getBytes(StandardCharsets.UTF_8), HMAC));
            mac.update((timestamp + "\n").getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(mac.doFinal(body), HexFormat.of().parseHex(signature))) reject();
            GenerationCallback callback = codec.validate(mapper.readTree(body), GenerationCallback.class);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST).digest(body));
            inbox.receive(provider, callback, hash, SystemActor.CREATIVE_RUNTIME.code());
        } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) { reject(); }
    }

    /** 回调可先于提交结果到达；没有绑定时保留 Inbox 等待，不丢弃也不重提作业。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void consume(GenerationCallbackInbox callback) {
        String actor = SystemActor.CREATIVE_RUNTIME.code();
        GenerationJob candidate = jobs.byProviderId(callback.getProvider(), callback.getProviderJobId()).orElse(null);
        if (Objects.isNull(candidate)) {
            if (callback.getCreatedAt().plus(properties.maxAge()).isBefore(LocalDateTime.now())) inbox.complete(callback.getId(), true, actor);
            else inbox.defer(callback.getId(), LocalDateTime.now().plus(properties.pollInterval()), actor);
            return;
        }
        runs.lockByRunId(candidate.getRunId()); GenerationJob job = jobs.lock(candidate.getJobId());
        boolean ignore = job.getStatus().isTerminal() || job.getStatus() == GenerationJobStatus.UNKNOWN;
        if (!inbox.complete(callback.getId(), ignore, actor) || ignore) return;
        job.setNextActionAt(LocalDateTime.now()); job.setUpdatedBy(actor); jobs.save(job, job.getOwnerEpoch());
        publisher.publishEvent(new GenerationJobSignal(job.getJobId()));
    }
    /** 拒绝时使用统一安全提示，不回显正文和签名。 */
    private void reject() { throw new BusinessException(GenerationError.CALLBACK, HttpStatus.UNAUTHORIZED); }
}
