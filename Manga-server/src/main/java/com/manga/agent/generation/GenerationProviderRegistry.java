package com.manga.agent.generation;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.common.exception.BusinessException;
import com.manga.entity.GenerationJob;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/** 按供应商和审批媒体类型注册独立实现。 */
@Component
public class GenerationProviderRegistry {
    private final Map<Route, GenerationProvider> providers;

    /** 同一供应商可以支持不同媒体，每个媒体只绑定一个执行器。 */
    public GenerationProviderRegistry(List<GenerationProvider> candidates) {
        Map<Route, GenerationProvider> map = new HashMap<>();
        for (GenerationProvider provider : candidates) {
            Route route = new Route(provider.key(), provider.mediaKind());
            if (Objects.nonNull(map.putIfAbsent(route, provider))) {
                throw new IllegalStateException(GenerationError.INVALID.message());
            }
        }
        providers = Map.copyOf(map);
    }

    /** 使用持久化提案类型精确查找所选供应商的执行器。 */
    public GenerationProvider require(GenerationJob job) {
        GenerationProvider result = providers.get(new Route(job.getProvider(), job.getMediaKind()));
        if (Objects.isNull(result)) throw new BusinessException(GenerationError.INVALID);
        return result;
    }

    private record Route(
            /** 供应商路由。 */ GenerationProviderKey provider,
            /** 已批准的生成提案类型。 */ ChangeSetKind kind
    ) { }
}
