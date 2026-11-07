package com.manga.agent.capability;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.manga.agent.domain.CreativeRuntimeMessages;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 定义可进入 Agent Blueprint 白名单的稳定能力标识。
 */
public enum CapabilityName {

    /** 读取项目基本信息和当前创作阶段。 */
    GET_PROJECT_OVERVIEW("get_project_overview"),
    /** 读取项目剧本元数据和章节目录。 */
    GET_SCRIPT_STRUCTURE("get_script_structure"),
    /** 读取项目内指定章节的正文与结构化结果。 */
    GET_CHAPTER_CONTENT("get_chapter_content"),
    /** 读取项目或章节下的分镜镜头列表。 */
    LIST_STORYBOARD_SHOTS("list_storyboard_shots"),
    /** 保存章节变更草稿，业务写入必须等待审批。 */
    PROPOSE_SCRIPT_CHANGE("propose_script_change"),
    /** 保存批量分镜草稿提案，业务写入必须等待审批。 */
    PROPOSE_STORYBOARD_CHANGE("propose_storyboard_change"),
    /** 提交镜头图片提案，真实计费提交必须等待用户批准。 */
    PROPOSE_IMAGE_GENERATION("propose_image_generation"),
    /** 持久化委派计划并暂停 Director。 */ DELEGATE_CREATIVE_PLAN("delegate_creative_plan"),
    /** Worker 提交经过 schema 校验的不可变结果。 */ SUBMIT_CREATIVE_STEP_RESULT("submit_creative_step_result"),
    /** 读取授权范围内已完成的协作结果。 */ READ_DELEGATED_RESULT("read_delegated_result"),
    /** 发现管理员已发布的专业 Worker 配置。 */ LIST_AGENT_PROFILES("list_agent_profiles");

    /** AgentScope 工具和快照使用的稳定名称。 */
    private final String code;

    /** 绑定模型工具调用和 Blueprint 快照共同使用的能力名称。 */
    CapabilityName(String code) {
        this.code = code;
    }

    /** 返回稳定能力名称。 */
    @JsonValue
    public String code() {
        return code;
    }

    /** 按稳定机器编码恢复能力标识。 */
    @JsonCreator
    public static CapabilityName fromCode(String code) {
        return Arrays.stream(values())
                .filter(name -> name.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        CreativeRuntimeMessages.CAPABILITY_UNKNOWN_TEMPLATE.formatted(code)));
    }

    /** 返回阶段二允许注册的完整只读能力集合。 */
    public static Set<CapabilityName> phaseTwoReadOnlyCapabilities() {
        return Set.of(GET_PROJECT_OVERVIEW, GET_SCRIPT_STRUCTURE, GET_CHAPTER_CONTENT, LIST_STORYBOARD_SHOTS);
    }

    /** Director 的完整工具上限，实际 Blueprint 再按请求权限缩小。 */
    public static Set<CapabilityName> directorCapabilities() {
        return Arrays.stream(values()).filter(name -> name != SUBMIT_CREATIVE_STEP_RESULT)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 返回剧本解析 Agent 所需的最小只读能力集合。 */
    public static Set<CapabilityName> scriptParserCapabilities() {
        return Set.of(GET_SCRIPT_STRUCTURE, GET_CHAPTER_CONTENT);
    }
}
