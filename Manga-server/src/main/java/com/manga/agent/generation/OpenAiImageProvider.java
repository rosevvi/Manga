package com.manga.agent.generation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.common.constant.AiProviderConstants;
import com.manga.dto.ResolvedAiProviderConfig;
import com.manga.entity.GenerationJob;
import com.manga.integration.ai.AiProviderProxySettings;
import com.manga.integration.http.ExternalHttpClientFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Objects;

/** OpenAI 同步 Images API 适配；没有可查询作业 ID，不冒充支持幂等重提或远程取消。 */
@Component
@RequiredArgsConstructor
public class OpenAiImageProvider implements GenerationProvider {
    private final ImageGenerationQuoteService quotes;
    private final ExternalHttpClientFactory clients;
    private final GenerationProperties properties;
    private final ChangeSetCodec codec;
    private final ObjectMapper mapper;

    /** 注册稳定供应商路由。 */
    @Override
    public GenerationProviderKey key() { return GenerationProviderKey.OPENAI; }
    /** 官方 Images API 未提供此实现可依赖的提交去重契约。 */
    @Override
    public boolean idempotentSubmission() { return false; }

    /** 使用审批时冻结参数发出一次 POST；网络、5xx 或协议异常一律保留未知责任。 */
    @Override
    public GenerationSubmission submit(GenerationJob job) {
        ResolvedAiProviderConfig config;
        try {
            config = quotes.resolve(job.getConfigId(), job.getUserId());
            GenerationConfigSnapshot frozen = codec.read(job.getConfigSnapshotJson(), GenerationConfigSnapshot.class);
            if (!frozen.equals(GenerationConfigSnapshot.from(config))) throw new GenerationException(GenerationError.AUTHENTICATION);
        } catch (GenerationException exception) { throw exception; }
        catch (RuntimeException exception) { throw new GenerationException(GenerationError.AUTHENTICATION); }
        ImageGenerationChange input = codec.read(job.getRequestJson(), ImageGenerationChange.class);
        AiProviderProxySettings proxy = new AiProviderProxySettings(config.proxyType(), config.proxyHost(),
                Objects.requireNonNullElse(config.proxyPort(), 0), config.proxyUsername(), config.proxyPassword());
        String base = config.baseUrl().replaceAll("/+$", "");
        if (!base.endsWith("/v1")) base += "/v1";
        OpenAiImageResponse result;
        try {
            ObjectMapper boundedMapper = mapper.copy();
            boundedMapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                    .maxStringLength(properties.maxMediaBytes() * 2).build());
            result = clients.createBoundedWebClient(base, proxy.toExternalProxySettings(), properties.httpTimeout(),
                            properties.maxMediaBytes() * 2)
                    .mutate().codecs(configurer -> configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(boundedMapper))).build()
                    .post().uri(GenerationConstants.IMAGE_PATH)
                    .header(HttpHeaders.AUTHORIZATION, AiProviderConstants.BEARER_PREFIX + config.apiKey())
                    .header(GenerationConstants.CLIENT_REQUEST_ID, job.getJobId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new OpenAiImageRequest(input.model(), input.prompt(), 1,
                            input.width() + "x" + input.height(), GenerationConstants.QUALITY, GenerationConstants.FORMAT))
                    .exchangeToMono(response -> {
                        int status = response.statusCode().value();
                        if (response.statusCode().is2xxSuccessful()) return response.bodyToMono(OpenAiImageResponse.class);
                        GenerationError error = switch (status) {
                            case 401, 403 -> GenerationError.AUTHENTICATION;
                            case 429 -> GenerationError.RATE_LIMIT;
                            case 400, 404, 422 -> GenerationError.REJECTED;
                            default -> GenerationError.SUBMISSION_UNKNOWN;
                        };
                        return response.releaseBody().then(Mono.error(new GenerationException(error)));
                    }).block(properties.httpTimeout());
        } catch (GenerationException exception) { throw exception; }
        catch (RuntimeException exception) { throw new GenerationException(GenerationError.SUBMISSION_UNKNOWN); }
        if (Objects.isNull(result) || Objects.isNull(result.data()) || result.data().size() != 1
                || Objects.isNull(result.data().getFirst().base64()) || result.data().getFirst().base64().isBlank()) {
            throw new GenerationException(GenerationError.SUBMISSION_UNKNOWN);
        }
        return new GenerationSubmission(null, new GenerationSnapshot(GenerationJobStatus.MATERIALIZING,
                null, result.data().getFirst().base64(), null));
    }

    /** 同步接口不支持查询；未知状态由人工核对，不通过另一个生成请求猜测。 */
    @Override
    public GenerationSnapshot query(GenerationJob job) { throw new GenerationException(GenerationError.SUBMISSION_UNKNOWN); }
    /** 本地只撤销采用权，不能承诺供应商停止执行或退款。 */
    @Override
    public void cancel(GenerationJob job) { }

    /** 严格冻结单张 PNG 请求，JSON 字段由对象声明。 */
    private record OpenAiImageRequest(
            /** 真实模型标识。 */ String model,
            /** 已批准的提示词。 */ String prompt,
            /** 固定一张。 */ int n,
            /** 已批准的像素尺寸。 */ String size,
            /** 固定质量。 */ String quality,
            /** 固定文件格式。 */ @JsonProperty("output_format") String outputFormat
    ) { }
    /** 只提取媒体，忽略不需要的供应商字段。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiImageResponse(/** 单张结果列表。 */ List<OpenAiImageData> data) { }
    /** 图片结果不暴露给公共接口。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiImageData(/** 有界 Base64 内容。 */ @JsonProperty("b64_json") String base64) { }
}
