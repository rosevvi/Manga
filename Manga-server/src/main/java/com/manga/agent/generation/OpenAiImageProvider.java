package com.manga.agent.generation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.integration.http.ExternalHttpClientFactory;
import com.manga.entity.GenerationJob;
import com.manga.entity.GenerationMediaObject;
import com.manga.service.MediaStorageService;
import com.manga.service.ProjectAssetService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

/** OpenAI Images API 的单张 PNG 请求适配。 */
@Component
public class OpenAiImageProvider extends SynchronousImageProvider {
    private final ImageGenerationConfigService imageConfigs;
    private final ProjectAssetService assets;
    private final MediaStorageService storage;
    private final GenerationProperties properties;

    /** 注入共享的图片提交依赖。 */
    public OpenAiImageProvider(ImageGenerationConfigService imageConfigs, ExternalHttpClientFactory clients,
            GenerationProperties properties, ChangeSetCodec codec, ObjectMapper mapper,
            ProjectAssetService assets, MediaStorageService storage) {
        super(imageConfigs, clients, properties, codec, mapper);
        this.imageConfigs = imageConfigs;
        this.assets = assets;
        this.storage = storage;
        this.properties = properties;
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

    /** 每次提交解析当前参考图，并发送文件内容。 */
    @Override
    protected ImageRequest prepareRequest(GenerationJob job, ImageGenerationChange input) {
        if (job.getTargetVisualId() == null) return super.prepareRequest(job, input);
        List<GenerationMediaObject> references;
        try {
            references = assets.currentReferenceMedia(job.getProjectId(), job.getTargetVisualId());
        } catch (RuntimeException exception) {
            throw new GenerationException(GenerationError.REFERENCE_UNAVAILABLE);
        }
        if (references.isEmpty()) return super.prepareRequest(job, input);
        if (!imageConfigs.supportsReferences(input.provider(), input.configSnapshot())) {
            throw new GenerationException(GenerationError.REFERENCES_UNSUPPORTED);
        }
        var body = new MultipartBodyBuilder();
        body.part("model", input.model());
        body.part("prompt", input.prompt());
        body.part("size", input.width() + "x" + input.height());
        body.part("quality", GenerationConstants.QUALITY);
        body.part("output_format", GenerationConstants.FORMAT);
        body.part("n", "1");
        int remaining = properties.maxMediaBytes();
        try {
            for (GenerationMediaObject reference : references) {
                byte[] bytes = storage.readGeneratedBytes(reference.reference(), remaining);
                remaining -= bytes.length;
                body.part("image[]", new ByteArrayResource(bytes), MediaType.IMAGE_PNG)
                        .filename(reference.getObjectName() + ".png");
            }
        } catch (RuntimeException exception) {
            throw new GenerationException(GenerationError.REFERENCE_UNAVAILABLE);
        }
        return new ImageRequest(GenerationConstants.IMAGE_EDIT_PATH, MediaType.MULTIPART_FORM_DATA, body.build());
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
