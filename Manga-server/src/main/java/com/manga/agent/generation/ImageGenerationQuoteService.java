package com.manga.agent.generation;

import com.manga.common.enums.AiProviderType;
import com.manga.common.exception.BusinessException;
import com.manga.dto.ResolvedAiProviderConfig;
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
        repository.findAllOwnedBy(userId).stream().filter(config -> Boolean.TRUE.equals(config.getEnabled())
                && COMPATIBLE_TYPES.contains(config.getProviderType())).forEach(config -> result.add(new ImageGenerationOption(
                GenerationProviderKey.OPENAI, config.getId(), config.getName(), GenerationConstants.OPENAI_MODEL,
                properties.imagePrices().get(GenerationConstants.OPENAI_MODEL), GenerationConstants.CURRENCY)));
        result.add(new ImageGenerationOption(GenerationProviderKey.FAKE, null, "免费流程验证（FAKE）", GenerationConstants.FAKE_MODEL,
                BigDecimal.ZERO, GenerationConstants.CURRENCY));
        return List.copyOf(result);
    }

    /** 只开放明确支持的三种尺寸及单张中等质量，服务端覆盖费用字段。 */
    public ImageGenerationChange canonicalize(ImageGenerationChange input, long userId) {
        boolean size = input.width() == 1024 && (input.height() == 1024 || input.height() == 1536)
                || input.width() == 1536 && input.height() == 1024;
        if (!size) throw new BusinessException(GenerationError.INVALID);
        BigDecimal price = BigDecimal.ZERO;
        GenerationConfigSnapshot snapshot = null;
        if (input.provider() == GenerationProviderKey.FAKE) {
            if (!GenerationConstants.FAKE_MODEL.equals(input.model())) throw new BusinessException(GenerationError.INVALID);
        } else {
            snapshot = GenerationConfigSnapshot.from(resolve(input.configId(), userId));
            if (!GenerationConstants.OPENAI_MODEL.equals(input.model())) throw new BusinessException(GenerationError.INVALID);
            price = properties.imagePrices().get(input.model());
            if (Objects.isNull(price) || price.signum() <= 0) throw new BusinessException(GenerationError.INVALID);
        }
        return new ImageGenerationChange(input.shotId(), input.provider(),
                input.provider() == GenerationProviderKey.FAKE ? null : input.configId(), input.model(), input.prompt().trim(),
                input.width(), input.height(), price, GenerationConstants.CURRENCY, snapshot);
    }

    /** 校验配置归属、启用状态和 HTTPS；网络地址只能来自用户已保存的配置。 */
    public ResolvedAiProviderConfig resolve(Long configId, long userId) {
        if (Objects.isNull(configId)) throw new BusinessException(GenerationError.INVALID);
        ResolvedAiProviderConfig config = configs.resolveOwned(userId, configId)
                .orElseThrow(() -> new BusinessException(GenerationError.AUTHENTICATION));
        URI endpoint;
        try { endpoint = URI.create(config.baseUrl()); }
        catch (IllegalArgumentException exception) { throw new BusinessException(GenerationError.INVALID); }
        if (!COMPATIBLE_TYPES.contains(config.providerType()) || !"https".equals(endpoint.getScheme())
                || Objects.isNull(endpoint.getHost()) || Objects.nonNull(endpoint.getUserInfo())
                || Objects.nonNull(endpoint.getQuery()) || Objects.nonNull(endpoint.getFragment())
                || Objects.isNull(config.apiKey()) || config.apiKey().isBlank()) throw new BusinessException(GenerationError.INVALID);
        return config;
    }
}
