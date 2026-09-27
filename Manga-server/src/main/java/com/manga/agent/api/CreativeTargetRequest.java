package com.manga.agent.api;

import com.manga.agent.domain.CreativeResourceReference;
import com.manga.agent.domain.CreativeResourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 表示手动创作请求直接作用的业务资源。
 */
public record CreativeTargetRequest(
        /** 目标业务资源类型。 */
        @NotNull CreativeResourceType resourceType,
        /** 目标业务资源主键。 */
        @Positive long resourceId
) {

    /** 转换为不依赖 Web 层的领域资源引用。 */
    public CreativeResourceReference toDomain() {
        return new CreativeResourceReference(resourceType, resourceId);
    }
}
