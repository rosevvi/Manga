package com.manga.agent.profile;

import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceType;

import java.util.Arrays;
import java.util.Optional;

/**
 * 将可由 AgentScope 执行的业务操作绑定到 Profile 和目标资源契约。
 */
public enum AgentOperationBinding {
    /** 由会话消息触发 Director 的只读对话，允许不绑定项目。 */
    DIRECTOR_CHAT(CreativeOperationType.ASSISTANT_CHAT, AgentProfileKey.MANGA_DIRECTOR, null),

    /** 将手动剧本解析操作绑定到只读解析 Profile，并要求目标为剧本。 */
    SCRIPT_PARSE(CreativeOperationType.PARSE_SCRIPT, AgentProfileKey.SCRIPT_PARSER, CreativeResourceType.SCRIPT),
    /** 手动为章节生成待审批分镜，不直接写镜头表。 */
    STORYBOARD_PROPOSAL(CreativeOperationType.GENERATE_CHAPTER_STORYBOARD, AgentProfileKey.MANGA_DIRECTOR, CreativeResourceType.CHAPTER),
    /** 手动创建章节提案；改写既有章节也可通过会话提出。 */
    SCRIPT_PROPOSAL(CreativeOperationType.GENERATE_SCRIPT_CHAPTER, AgentProfileKey.MANGA_DIRECTOR, CreativeResourceType.PROJECT);

    /** 创建 Run 时用于选择 Agent 执行链路的业务操作。 */
    private final CreativeOperationType operationType;
    /** 受理阶段编译 Blueprint 使用的内置 Agent Profile。 */
    private final AgentProfileKey profileKey;
    /** 受理阶段必须校验的直接目标资源类型。 */
    private final CreativeResourceType targetType;

    /** 固定业务操作、Agent Profile 与目标资源之间的路由关系。 */
    AgentOperationBinding(
            CreativeOperationType operationType,
            AgentProfileKey profileKey,
            CreativeResourceType targetType) {
        this.operationType = operationType;
        this.profileKey = profileKey;
        this.targetType = targetType;
    }

    /** 返回当前绑定使用的内置 Agent Profile。 */
    public AgentProfileKey profileKey() {
        return profileKey;
    }

    /** 返回当前操作要求的目标资源类型。 */
    public CreativeResourceType targetType() {
        return targetType;
    }

    /** 查询业务操作是否由真实 Agent 执行。 */
    public static Optional<AgentOperationBinding> find(CreativeOperationType operationType) {
        return Arrays.stream(values())
                .filter(binding -> binding.operationType == operationType)
                .findFirst();
    }
}
