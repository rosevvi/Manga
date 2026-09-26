package com.manga.agent.config;

import com.manga.agent.domain.CreativeRuntimeType;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 配置创作运行时的默认实现及用户、项目灰度覆盖规则。
 */
@Validated
@ConfigurationProperties(prefix = "manga.agent.routing")
public class CreativeRuntimeRoutingProperties {

    /** 未命中覆盖规则时使用的运行时。 */
    @NotNull
    private CreativeRuntimeType defaultRuntime = CreativeRuntimeType.LEGACY;

    /** 用户级运行时覆盖，键为用户主键。 */
    private Map<Long, CreativeRuntimeType> userOverrides = new LinkedHashMap<>();

    /** 项目级运行时覆盖，键为项目主键。 */
    private Map<Long, CreativeRuntimeType> projectOverrides = new LinkedHashMap<>();

    public CreativeRuntimeType getDefaultRuntime() {
        return defaultRuntime;
    }

    public void setDefaultRuntime(CreativeRuntimeType defaultRuntime) {
        this.defaultRuntime = defaultRuntime;
    }

    public Map<Long, CreativeRuntimeType> getUserOverrides() {
        return userOverrides;
    }

    public void setUserOverrides(Map<Long, CreativeRuntimeType> userOverrides) {
        this.userOverrides = Objects.isNull(userOverrides) ? new LinkedHashMap<>() : new LinkedHashMap<>(userOverrides);
    }

    public Map<Long, CreativeRuntimeType> getProjectOverrides() {
        return projectOverrides;
    }

    public void setProjectOverrides(Map<Long, CreativeRuntimeType> projectOverrides) {
        this.projectOverrides = Objects.isNull(projectOverrides) ? new LinkedHashMap<>() : new LinkedHashMap<>(projectOverrides);
    }
}
