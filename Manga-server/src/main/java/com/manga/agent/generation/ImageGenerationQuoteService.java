package com.manga.agent.generation;

import com.manga.common.enums.AiProviderType;
import com.manga.common.enums.AiModelType;
import com.manga.common.exception.BusinessException;
import com.manga.dto.ResolvedAiProviderConfig;
import com.manga.entity.AiProviderConfig;
import com.manga.integration.ai.AgnesEndpoint;
import com.manga.service.AiProviderConfigService;
import com.manga.repository.AiProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/** 生成前解析用户配置、允许模型和预计费用，禁止模型伪造价格或端点。 */
@Service
@RequiredArgsConstructor
public class ImageGenerationQuoteService {
    private final AiProviderConfigService configs;
    private final GenerationProperties properties;
    private final AiProviderConfigRepository repository;
    /** 支持官方 OpenAI 与明确配置的兼容网关。 */
    private static final Set<AiProviderType> COMPATIBLE_TYPES = Set.of(AiProviderType.OPENAI, AiProviderType.OPENAI_COMPATIBLE, AiProviderType.NEWAPI);

    /** 仅返回选择图片端点所需摘要，避免通用配置管理 DTO 携带凭据。 */
    public List<ImageGenerationOption> options(long userId) {
        List<ImageGenerationOption> result = new ArrayList<>();
        for (AiProviderConfig config : repository.findAllOwnedBy(userId)) {
            if (!Boolean.TRUE.equals(config.getEnabled()) || config.getModelType() != AiModelType.IMAGE
                    || Objects.isNull(config.getApiKeyCiphertext())) continue;
            GenerationProviderKey provider = route(config.getProviderType(), config.getDefaultModel(), config.getBaseUrl());
            if (Objects.nonNull(provider)) {
                result.add(new ImageGenerationOption(provider, config.getId(), config.getName(), config.getDefaultModel(),
                        properties.imagePrices().get(config.getDefaultModel()), GenerationConstants.CURRENCY,
                        ImageGenerationSize.options(provider)));
            }
        }
        result.add(new ImageGenerationOption(GenerationProviderKey.FAKE, null, "免费流程验证（FAKE）", GenerationConstants.FAKE_MODEL,
                BigDecimal.ZERO, GenerationConstants.CURRENCY, ImageGenerationSize.options(GenerationProviderKey.FAKE)));
        return List.copyOf(result);
    }

    /** 校验供应商的原生尺寸，服务端按用户指定配置冻结模型、端点和费用。 */
    public ImageGenerationChange canonicalize(ImageGenerationChange input, long userId) {
        ImageGenerationSize.require(input.provider(), input.width(), input.height());
        BigDecimal price = BigDecimal.ZERO;
        GenerationConfigSnapshot snapshot = null;
        if (input.provider() == GenerationProviderKey.FAKE) {
            if (!GenerationConstants.FAKE_MODEL.equals(input.model())) throw new BusinessException(GenerationError.INVALID);
        } else {
            ResolvedAiProviderConfig config = resolve(input.provider(), input.configId(), userId);
            if (!Objects.equals(config.defaultModel(), input.model())) throw new BusinessException(GenerationError.INVALID);
            snapshot = GenerationConfigSnapshot.from(config);
            price = properties.imagePrices().get(input.model());
            if (Objects.isNull(price) || price.signum() <= 0) throw new BusinessException(GenerationError.INVALID);
        }
        return new ImageGenerationChange(input.shotId(), input.provider(),
                input.provider() == GenerationProviderKey.FAKE ? null : input.configId(), input.model(), input.prompt().trim(),
                input.width(), input.height(), price, GenerationConstants.CURRENCY, snapshot);
    }

    /** 校验配置归属、启用、供应商和模型，拒绝使用错误的凭据或端点。 */
    public ResolvedAiProviderConfig resolve(GenerationProviderKey provider, Long configId, long userId) {
        if (Objects.isNull(configId)) throw new BusinessException(GenerationError.INVALID);
        ResolvedAiProviderConfig config = configs.resolveOwned(userId, configId, AiModelType.IMAGE)
                .orElseThrow(() -> new BusinessException(GenerationError.AUTHENTICATION));
        if (Objects.isNull(provider) || route(config.providerType(), config.defaultModel(), config.baseUrl()) != provider
                || Objects.isNull(config.apiKey()) || config.apiKey().isBlank()) throw new BusinessException(GenerationError.INVALID);
        return config;
    }

    /** 配置列表和作业执行使用相同的供应商识别规则。 */
    private GenerationProviderKey route(AiProviderType type, String model, String address) {
        if (type == AiProviderType.AGNES && AgnesEndpoint.supports(address) && AgnesImageModel.find(model).isPresent()) {
            return GenerationProviderKey.AGNES;
        }
        if (Objects.isNull(type) || !COMPATIBLE_TYPES.contains(type) || !GenerationConstants.OPENAI_MODEL.equals(model)
                || Objects.isNull(address)) return null;
        try {
            URI endpoint = URI.create(address);
            if ("https".equals(endpoint.getScheme()) && Objects.nonNull(endpoint.getHost())
                    && Objects.isNull(endpoint.getUserInfo()) && Objects.isNull(endpoint.getQuery())
                    && Objects.isNull(endpoint.getFragment())) return GenerationProviderKey.OPENAI;
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return null;
    }
}
