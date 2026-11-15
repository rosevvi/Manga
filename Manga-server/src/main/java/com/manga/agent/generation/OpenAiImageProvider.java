package com.manga.agent.generation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.integration.http.ExternalHttpClientFactory;
import org.springframework.stereotype.Component;

/** OpenAI Images API 的单张 PNG 请求适配。 */
@Component
public class OpenAiImageProvider extends SynchronousImageProvider {
    /** 注入共享的图片提交依赖。 */
    public OpenAiImageProvider(ImageGenerationQuoteService quotes, ExternalHttpClientFactory clients,
            GenerationProperties properties, ChangeSetCodec codec, ObjectMapper mapper) {
        super(quotes, clients, properties, codec, mapper);
    }

    /** 注册 OpenAI 和兼容网关的图片路由。 */
    @Override
    public GenerationProviderKey key() { return GenerationProviderKey.OPENAI; }

    /** 保留 OpenAI 的精确像素尺寸和中等质量。 */
    @Override
    protected Object requestBody(ImageGenerationChange input) {
        return new Request(input.model(), input.prompt(), 1, input.width() + "x" + input.height(),
                GenerationConstants.QUALITY, GenerationConstants.FORMAT);
    }

    private record Request(
            /** 已冻结的模型。 */ String model,
            /** 已批准的提示词。 */ String prompt,
            /** 每个作业固定一张。 */ int n,
            /** 产物的精确像素尺寸。 */ String size,
            /** 固定为中等质量。 */ String quality,
            /** 产物文件格式。 */ @JsonProperty("output_format") String outputFormat
    ) { }
}
