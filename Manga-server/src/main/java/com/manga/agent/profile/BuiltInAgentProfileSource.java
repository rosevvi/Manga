package com.manga.agent.profile;

import com.manga.agent.capability.CapabilityName;
import com.manga.agent.config.CreativeAgentProperties;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeRuntimeMessages;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 从受版本控制的资源构建平台内置 Agent Profile。
 */
@Component
public class BuiltInAgentProfileSource implements AgentProfileSource {

    private final Map<AgentProfileKey, AgentProfile> profiles;

    /** 加载系统提示词并建立不可变 Profile 索引。 */
    public BuiltInAgentProfileSource(CreativeAgentProperties properties, ResourceLoader resourceLoader) {
        EnumMap<AgentProfileKey, AgentProfile> indexed = new EnumMap<>(AgentProfileKey.class);
        AgentProfile director = new AgentProfile(
                AgentProfileKey.MANGA_DIRECTOR,
                properties.directorProfileVersion(),
                "漫剧创作导演",
                "读取项目、剧本和分镜内容，为用户提供分析与创作建议。",
                readPrompt(resourceLoader, properties.directorPromptResource()),
                CapabilityName.phaseTwoReadOnlyCapabilities(),
                AgentPermissionPolicy.READ_ONLY,
                properties.directorMaxTurns()
        );
        register(indexed, director);
        AgentProfile scriptParser = new AgentProfile(
                AgentProfileKey.SCRIPT_PARSER,
                properties.scriptParserProfileVersion(),
                "剧本解析师",
                "读取项目剧本和章节正文，输出结构化的剧本分析报告。",
                readPrompt(resourceLoader, properties.scriptParserPromptResource()),
                CapabilityName.scriptParserCapabilities(),
                AgentPermissionPolicy.READ_ONLY,
                properties.scriptParserMaxTurns()
        );
        register(indexed, scriptParser);
        profiles = Map.copyOf(indexed);
    }

    /** 返回指定内置 Profile，不存在时拒绝继续编译 Blueprint。 */
    @Override
    public AgentProfile require(AgentProfileKey key) {
        AgentProfile profile = profiles.get(key);
        if (Objects.isNull(profile)) {
            throw new IllegalArgumentException(
                    CreativeRuntimeMessages.AGENT_PROFILE_NOT_FOUND_TEMPLATE.formatted(key));
        }
        return profile;
    }

    /** 注册 Profile 并拒绝重复机器标识。 */
    private void register(Map<AgentProfileKey, AgentProfile> target, AgentProfile profile) {
        if (Objects.nonNull(target.put(profile.key(), profile))) {
            throw new IllegalStateException(
                    CreativeRuntimeMessages.AGENT_PROFILE_DUPLICATED_TEMPLATE.formatted(profile.key()));
        }
    }

    /** 读取受版本控制的系统提示词资源。 */
    private String readPrompt(ResourceLoader resourceLoader, String location) {
        Resource resource = resourceLoader.getResource(location);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    CreativeRuntimeMessages.AGENT_PROFILE_NOT_FOUND_TEMPLATE.formatted(location), exception);
        }
    }
}
