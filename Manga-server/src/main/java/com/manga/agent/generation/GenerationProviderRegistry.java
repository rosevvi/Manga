package com.manga.agent.generation;
import com.manga.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/** 按枚举注册供应商，拒绝重复和未知实现。 */
@Component
public class GenerationProviderRegistry {
    private final Map<GenerationProviderKey,GenerationProvider> providers;
    /** 构建唯一供应商索引。 */
    public GenerationProviderRegistry(List<GenerationProvider> candidates) {
        var map=new EnumMap<GenerationProviderKey,GenerationProvider>(GenerationProviderKey.class);
        for(var provider:candidates) if(Objects.nonNull(map.put(provider.key(),provider))) throw new IllegalStateException(GenerationError.INVALID.message());
        providers=Map.copyOf(map);
    }
    /** 精确查找实现，不自动切换到其他收费供应商。 */
    public GenerationProvider require(GenerationProviderKey key) {
        GenerationProvider result=providers.get(key);
        if(Objects.isNull(result)) throw new BusinessException(GenerationError.INVALID);
        return result;
    }
}
