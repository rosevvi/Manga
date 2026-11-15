package com.manga.agent.generation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.integration.http.ExternalHttpClientFactory;
import org.springframework.stereotype.Component;

/** Agnes 同步文生图协议，将批准的尺寸转换为官方尺寸档位及画幅。 */
@Component
public class AgnesImageProvider extends SynchronousImageProvider {
    /** 当前开放尺寸均为原生 1K。 */
    private static final String SIZE_TIER = "1K";

    /** 注入共享的图片提交依赖。 */
    public AgnesImageProvider(ImageGenerationQuoteService quotes, ExternalHttpClientFactory clients,
            GenerationProperties properties, ChangeSetCodec codec, ObjectMapper mapper) {
        super(quotes, clients, properties, codec, mapper);
    }

    /** 注册 Agnes 图片路由。 */
    @Override
    public GenerationProviderKey key() { return GenerationProviderKey.AGNES; }

    /** 文生图返回 Base64，交给已有的 PNG 校验及产物转存流程。 */
    @Override
    protected Object requestBody(ImageGenerationChange input) {
        ImageGenerationSize size = ImageGenerationSize.require(key(), input.width(), input.height());
        return new Request(input.model(), input.prompt(), SIZE_TIER, size.agnesRatio(), true);
    }

    private record Request(
            /** 当前用户配置且已批准的模型。 */ String model,
            /** 已批准的提示词。 */ String prompt,
            /** Agnes 输出分辨率档位。 */ String size,
            /** 本次输出画幅。 */ String ratio,
            /** 文生图使用供应商文档规定的 Base64 返回参数。 */ @JsonProperty("return_base64") boolean returnBase64
    ) { }
}
