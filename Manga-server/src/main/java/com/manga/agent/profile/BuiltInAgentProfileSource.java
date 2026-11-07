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
import com.manga.agent.collaboration.CollaborationProperties;
import com.manga.agent.collaboration.CollaborationMessages;
import com.manga.agent.collaboration.StepResultKind;
import java.util.Map;
import java.util.Objects;

/**
 * 从受版本控制的资源构建平台内置 Agent Profile。
 */
@Component
public class BuiltInAgentProfileSource implements AgentProfileSource {

    private final Map<AgentProfileKey, AgentProfile> profiles;

    /** 加载系统提示词并建立不可变 Profile 索引。 */
    public BuiltInAgentProfileSource(CreativeAgentProperties properties, ResourceLoader resourceLoader, CollaborationProperties collaboration) {
        EnumMap<AgentProfileKey, AgentProfile> indexed = new EnumMap<>(AgentProfileKey.class);
        AgentProfile director = new AgentProfile(
                AgentProfileKey.MANGA_DIRECTOR,
                properties.directorProfileVersion(),
                "漫剧创作导演",
                "读取项目内容、提出章节和分镜变更，等待用户审批后继续。",
                readPrompt(resourceLoader, properties.directorPromptResource()),
                CapabilityName.directorCapabilities(),
                AgentPermissionPolicy.PROPOSE_CHANGES,
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
        registerWorker(indexed, AgentProfileKey.SCRIPT_WRITER, "剧本编剧", StepResultKind.SCRIPT_DRAFT, collaboration);
        registerWorker(indexed, AgentProfileKey.STORYBOARD_DIRECTOR, "分镜导演", StepResultKind.STORYBOARD_DRAFT, collaboration);
        registerWorker(indexed, AgentProfileKey.CONTINUITY_REVIEWER, "连续性评审师", StepResultKind.REVIEW, collaboration);
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

    /** Worker 只读取项目并产出结果，实际工具清单在委派时按父权限继续收窄。 */
    private void registerWorker(Map<AgentProfileKey, AgentProfile> target, AgentProfileKey key,
            String name, StepResultKind kind, CollaborationProperties properties) {
        register(target, new AgentProfile(key, properties.workerProfileVersion(), name, name,
                CollaborationMessages.WORKER_TEMPLATE.formatted(name, kind),
                CapabilityName.phaseTwoReadOnlyCapabilities(), AgentPermissionPolicy.READ_ONLY,
                properties.workerMaxTurns()));
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
