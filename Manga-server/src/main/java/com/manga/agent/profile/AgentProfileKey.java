package com.manga.agent.profile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.manga.agent.domain.CreativeRuntimeMessages;

import java.util.Arrays;

/**
 * 定义固定执行角色族及同名内置 Profile；管理员专业配置身份保存在 AgentExecutionPolicy。
 */
public enum AgentProfileKey {

    /** 负责理解用户目标并使用只读能力分析项目的漫剧导演。 */
    MANGA_DIRECTOR("manga_director"),

    /** 负责读取项目剧本并输出结构化解析报告的专业 Agent。 */
    SCRIPT_PARSER("script_parser"),
    /** 产出章节候选草稿的只读 Worker。 */ SCRIPT_WRITER("script_writer"),
    /** 产出分镜候选草稿的只读 Worker。 */ STORYBOARD_DIRECTOR("storyboard_director"),
    /** 审核上游候选内容连贯性的只读 Worker。 */ CONTINUITY_REVIEWER("continuity_reviewer");

    /** 持久化和指纹计算使用的稳定编码。 */
    private final String code;

    /** 绑定用于 JSON 快照、工具诊断和指纹计算的稳定编码。 */
    AgentProfileKey(String code) {
        this.code = code;
    }

    /** 返回稳定机器编码。 */
    @JsonValue
    public String code() {
        return code;
    }

    /** 按稳定机器编码恢复 Profile 标识。 */
    @JsonCreator
    public static AgentProfileKey fromCode(String code) {
        return Arrays.stream(values())
                .filter(key -> key.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        CreativeRuntimeMessages.AGENT_PROFILE_UNKNOWN_TEMPLATE.formatted(code)));
    }
}
