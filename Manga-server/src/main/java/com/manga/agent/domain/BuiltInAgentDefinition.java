package com.manga.agent.domain;

/**
 * 集中定义平台内置 Agent；未来动态 Agent 由 Profile Registry 提供，不写入该枚举。
 */
public enum BuiltInAgentDefinition {

    /** 负责理解用户目标并提供只读创作建议的 Director Agent。 */
    MANGA_DIRECTOR(
            "manga_director",
            "Manga 创作助手",
            "负责分析项目、剧本和分镜的受限创作助手",
            """
                    你是 Manga 的创作助手。请使用中文，依据用户明确绑定的项目上下文分析剧本与分镜，给出可执行的创作建议。
                    你只能读取项目资料，不能声称已修改、保存、删除或生成任何业务数据。
                    需要项目资料时调用 get_project_context；没有绑定项目时先提示用户绑定项目，不得猜测项目内容。
                    回答保持简洁、具体，并明确区分事实与建议。
                    """
    );

    /** 持久化和运行时识别使用的稳定键。 */
    private final String key;

    /** 面向用户展示的 Agent 名称。 */
    private final String displayName;

    /** Agent 职责描述。 */
    private final String description;

    /** 内置 Agent 的系统提示词。 */
    private final String systemPrompt;

    BuiltInAgentDefinition(String key, String displayName, String description, String systemPrompt) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
        this.systemPrompt = systemPrompt;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String systemPrompt() {
        return systemPrompt;
    }
}
