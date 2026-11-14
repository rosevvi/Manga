package com.manga.agent.generation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.dto.ResolvedAiProviderConfig;
import com.manga.entity.GenerationJob;
import com.manga.integration.ai.AiProviderProxySettings;
import com.manga.integration.http.ExternalHttpClientFactory;
import com.manga.service.storage.GeneratedMediaFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Agnes 视频协议适配。付费提交只发一次，状态查询及结果下载可以重试。 */
@Component
@RequiredArgsConstructor
public class AgnesVideoProvider implements GenerationProvider {
    private static final String CREATE_PATH = "/v1/videos";
    private static final String QUERY_PATH = "/agnesapi";
    private static final String TEXT_MODE = "text";
    private static final int RESPONSE_LIMIT = 64 * 1024;
    private static final Duration API_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Set<String> RESULT_HOSTS = Set.of("platform-outputs.agnes-ai.space", "apihub.agnes-ai.com");
    private static final int MP4_FILE_TYPE_BOX = 0x66747970;

    private final VideoGenerationQuoteService quotes;
    private final ExternalHttpClientFactory clients;
    private final GenerationProperties properties;
    private final ChangeSetCodec codec;

    /** 注册 Agnes 视频路由。 */
    @Override
    public GenerationProviderKey key() { return GenerationProviderKey.AGNES; }

    /** Agnes 返回 MP4 文件。 */
    @Override
    public GeneratedMediaFormat format() { return GeneratedMediaFormat.MP4; }

    /** Agnes 未声明幂等提交。超时后由作业进入人工核对。 */
    @Override
    public boolean idempotentSubmission() { return false; }

    /** 提交已审批的参数，只记录 video_id 作为轮询标识。 */
    @Override
    public GenerationSubmission submit(GenerationJob job) {
        ResolvedAiProviderConfig config = resolve(job);
        var input = codec.read(job.getRequestJson(), VideoGenerationChange.class);
        if (!input.model().equals(config.defaultModel())) throw new GenerationException(GenerationError.AUTHENTICATION);
        var request = new VideoRequest(input.model(), input.prompt(), TEXT_MODE,
                Integer.toString(input.seconds()), AgnesVideoModel.SIZE, input.aspectRatio());
        VideoResponse response = exchange(client(config).post().uri(CREATE_PATH)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request), true);
        if (response.videoId() == null || !VIDEO_ID.matcher(response.videoId()).matches()) {
            throw new GenerationException(GenerationError.SUBMISSION_UNKNOWN);
        }
        return new GenerationSubmission(response.videoId(), snapshot(response, job.getStatus()));
    }

    /** 用冻结模型及供应商视频 ID 读取事实，忽略供应商内部进度字段。 */
    @Override
    public GenerationSnapshot query(GenerationJob job) {
        ResolvedAiProviderConfig config = resolve(job);
        var input = codec.read(job.getRequestJson(), VideoGenerationChange.class);
        if (job.getProviderJobId() == null || !VIDEO_ID.matcher(job.getProviderJobId()).matches()) {
            throw new GenerationException(GenerationError.PROTOCOL);
        }
        VideoResponse response = exchange(client(config).get().uri(builder -> builder.path(QUERY_PATH)
                .queryParam("video_id", job.getProviderJobId()).queryParam("model_name", input.model()).build()), false);
        return snapshot(response, job.getStatus());
    }

    /** 文档未承诺远端取消，本地只撤销产物发布。 */
    @Override
    public void cancel(GenerationJob job) { }

    /** 有界下载可信媒体来源，不发送 API Key、不重定向，校验 MP4 容器标识。 */
    @Override
    public byte[] download(GenerationJob job) {
        URI uri = resultUri(job.getResultUrl());
        ResolvedAiProviderConfig config = resolve(job);
        byte[] bytes;
        try {
            bytes = clients.createBoundedWebClient(AgnesVideoModel.BASE_URL, proxy(config).toExternalProxySettings(),
                            properties.httpTimeout(), properties.maxMediaBytes())
                    .get().uri(uri).retrieve().bodyToMono(byte[].class).block(properties.httpTimeout());
        } catch (RuntimeException exception) {
            throw new GenerationException(GenerationError.STORAGE);
        }
        if (bytes == null || bytes.length < 16 || ByteBuffer.wrap(bytes).getInt(4) != MP4_FILE_TYPE_BOX) {
            throw new GenerationException(GenerationError.PROTOCOL);
        }
        return bytes;
    }

    /** 限定下载到供应商媒体域名，禁止重定向到内部地址。 */
    static URI resultUri(String value) {
        if (value == null || value.length() > 4096) throw new GenerationException(GenerationError.PROTOCOL);
        try {
            URI uri = URI.create(value);
            if ("https".equals(uri.getScheme()) && uri.getHost() != null && RESULT_HOSTS.contains(uri.getHost())
                    && uri.getPort() == -1 && uri.getUserInfo() == null && uri.getFragment() == null
                    && uri.getPath() != null && uri.getPath().endsWith(".mp4")) return uri;
        } catch (IllegalArgumentException exception) {
            throw new GenerationException(GenerationError.PROTOCOL);
        }
        throw new GenerationException(GenerationError.PROTOCOL);
    }

    /** 只采用已知状态，排队报告不能使运行中的平台作业倒退。 */
    static GenerationSnapshot snapshot(VideoResponse result, GenerationJobStatus current) {
        Integer progress = result.progress();
        return switch (Objects.toString(result.status(), "")) {
            case "queued", "pending", "submitted" -> new GenerationSnapshot(
                    current == GenerationJobStatus.RUNNING ? current : GenerationJobStatus.SUBMITTED, progress, null, null);
            case "in_progress", "processing", "running" -> new GenerationSnapshot(GenerationJobStatus.RUNNING, progress, null, null);
            case "completed", "succeeded" -> new GenerationSnapshot(GenerationJobStatus.MATERIALIZING, progress, null, null,
                    resultUri(result.url()).toString());
            case "failed" -> new GenerationSnapshot(GenerationJobStatus.FAILED, progress, null, GenerationError.FAILED);
            case "cancelled", "canceled" -> new GenerationSnapshot(GenerationJobStatus.CANCELLED, progress, null, null);
            default -> throw new GenerationException(GenerationError.PROTOCOL);
        };
    }

    /** 校验供应商端点未变，凭据允许正常轮换。 */
    private ResolvedAiProviderConfig resolve(GenerationJob job) {
        try {
            var resolved = quotes.resolve(job.getConfigId(), job.getUserId());
            if (GenerationConfigSnapshot.from(resolved).equals(codec.read(job.getConfigSnapshotJson(),
                    GenerationConfigSnapshot.class))) return resolved;
        } catch (RuntimeException exception) {
            throw new GenerationException(GenerationError.AUTHENTICATION);
        }
        throw new GenerationException(GenerationError.AUTHENTICATION);
    }

    /** 同一个官方网关处理认证请求，不向媒体下载传递认证头。 */
    private WebClient client(ResolvedAiProviderConfig config) {
        return clients.createBoundedWebClient(AgnesVideoModel.BASE_URL, proxy(config).toExternalProxySettings(),
                API_TIMEOUT, RESPONSE_LIMIT).mutate().defaultHeaders(headers -> headers.setBearerAuth(config.apiKey())).build();
    }

    /** 复用该用户保存的出站代理配置。 */
    private AiProviderProxySettings proxy(ResolvedAiProviderConfig config) {
        return new AiProviderProxySettings(config.proxyType(), config.proxyHost(),
                Objects.requireNonNullElse(config.proxyPort(), 0), config.proxyUsername(), config.proxyPassword());
    }

    /** 提交不确定时进入 UNKNOWN，轮询网络失败仅退避。 */
    private VideoResponse exchange(WebClient.RequestHeadersSpec<?> request, boolean submitting) {
        GenerationError fallback = submitting ? GenerationError.SUBMISSION_UNKNOWN : GenerationError.TRANSIENT;
        try {
            VideoResponse value = request.exchangeToMono(response -> {
                if (response.statusCode().is2xxSuccessful()) return response.bodyToMono(VideoResponse.class);
                GenerationError error = switch (response.statusCode().value()) {
                    case 401, 403 -> GenerationError.AUTHENTICATION;
                    case 429 -> GenerationError.RATE_LIMIT;
                    case 400, 404, 422 -> submitting ? GenerationError.REJECTED : fallback;
                    default -> fallback;
                };
                return response.releaseBody().then(Mono.error(new GenerationException(error)));
            }).block(API_TIMEOUT);
            if (value != null) return value;
        } catch (GenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GenerationException(fallback);
        }
        throw new GenerationException(fallback);
    }

    /** 出站 JSON 只包含此版本开放的生成参数。 */
    private record VideoRequest(
            /** 模型。 */ String model,
            /** 用户审批过的提示词。 */ String prompt,
            /** 生成模式。 */ String mode,
            /** 字符串表示的输出秒数。 */ String seconds,
            /** 输出分辨率。 */ String size,
            /** 输出画幅。 */ @JsonProperty("aspect_ratio") String aspectRatio) { }

    /** Agnes 提交和状态查询所需的最小响应。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record VideoResponse(
            /** 查询标识。 */ @JsonProperty("video_id") String videoId,
            /** 对外状态。 */ String status,
            /** 对外进度。 */ Integer progress,
            /** 完成后的下载地址。 */ String url) { }
}
