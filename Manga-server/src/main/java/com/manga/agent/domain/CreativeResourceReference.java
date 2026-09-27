package com.manga.agent.domain;

import java.util.Objects;

/**
 * 指向创作运行直接处理的业务资源。
 */
public record CreativeResourceReference(
        /** 目标业务资源类型。 */
        CreativeResourceType resourceType,
        /** 目标业务资源主键。 */
        long resourceId
) {

    public CreativeResourceReference {
        Objects.requireNonNull(resourceType, CreativeRuntimeMessages.RESOURCE_TYPE_REQUIRED);
        if (resourceId <= 0) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.RESOURCE_ID_MUST_BE_POSITIVE);
        }
    }
}
