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
        Objects.requireNonNull(resourceType, "创作目标资源类型不能为空");
        if (resourceId <= 0) {
            throw new IllegalArgumentException("创作目标资源主键必须大于零");
        }
    }
}
