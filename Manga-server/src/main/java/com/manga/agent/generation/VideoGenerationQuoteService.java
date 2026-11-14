package com.manga.agent.generation;

import com.manga.common.enums.AiModelType;
import com.manga.common.enums.AiProviderType;
import com.manga.common.exception.BusinessException;
import com.manga.dto.ResolvedAiProviderConfig;
import com.manga.repository.AiProviderConfigRepository;
import com.manga.service.AiProviderConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 从当前用户自己的视频配置获取模型、连接及单次费用。 */
@Service
@RequiredArgsConstructor
public class VideoGenerationQuoteService {
    private static final Set<String> RATIOS = Set.of("16:9", "9:16", "1:1", "4:3", "3:4", "21:9");
    private final AiProviderConfigRepository repository;
    private final AiProviderConfigService configs;

    /** 默认配置由仓储排序优先返回，列表只包含当前支持的视频模型。 */
    public List<ImageGenerationOption> options(long userId) {
        return repository.findAllOwnedBy(userId).stream()
                .filter(config -> config.getModelType() == AiModelType.VIDEO && config.getProviderType() == AiProviderType.AGNES)
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()) && config.getApiKeyCiphertext() != null)
                .filter(config -> supportsEndpoint(config.getBaseUrl()) && AgnesVideoModel.find(config.getDefaultModel()).isPresent())
                .map(config -> new ImageGenerationOption(GenerationProviderKey.AGNES, config.getId(),
                        config.getName(), config.getDefaultModel(), AgnesVideoModel.USD_PER_SECOND, GenerationConstants.CURRENCY))
                .toList();
    }

    /** 重算费用并冻结用户所选模型，批准时使用同一规则检查配置是否变化。 */
    public VideoGenerationChange canonicalize(VideoGenerationChange input, long userId) {
        if (!RATIOS.contains(Objects.toString(input.aspectRatio(), ""))) throw new BusinessException(GenerationError.INVALID);
        Long configId = input.configId() == null ? options(userId).stream().findFirst().map(ImageGenerationOption::configId)
                .orElseThrow(() -> new BusinessException(GenerationError.INVALID)) : input.configId();
        ResolvedAiProviderConfig config = resolve(configId, userId);
        BigDecimal estimate = AgnesVideoModel.USD_PER_SECOND.multiply(BigDecimal.valueOf(input.seconds()));
        return new VideoGenerationChange(input.shotId(), configId, input.prompt().trim(), input.seconds(), input.aspectRatio(),
                config.defaultModel(), estimate, GenerationConstants.CURRENCY, GenerationConfigSnapshot.from(config));
    }

    /** 运行时校验类型、归属、启用和官方端点，不允许将 Agnes 密钥发送到自填域名。 */
    public ResolvedAiProviderConfig resolve(long configId, long userId) {
        return configs.resolveOwned(userId, configId, AiModelType.VIDEO)
                .filter(value -> value.providerType() == AiProviderType.AGNES && supportsEndpoint(value.baseUrl()))
                .filter(value -> AgnesVideoModel.find(value.defaultModel()).isPresent() && value.apiKey() != null && !value.apiKey().isBlank())
                .orElseThrow(() -> new BusinessException(GenerationError.AUTHENTICATION));
    }

    /** 允许用户填官方根地址或带 /v1 的文档地址。 */
    public static boolean supportsEndpoint(String address) {
        return AgnesVideoModel.BASE_URL.equals(address) || (AgnesVideoModel.BASE_URL + "/v1").equals(address);
    }
}
