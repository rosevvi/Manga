package com.manga.agent.domain;

import org.springframework.util.StringUtils;

/**
 * 表示当前服务进程参与运行租约竞争时使用的唯一身份。
 */
public record RuntimeInstanceIdentity(
        /** 可配置或在启动时生成的实例唯一标识。 */
        String value
) {

    public RuntimeInstanceIdentity {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("运行时实例标识不能为空");
        }
    }
}
