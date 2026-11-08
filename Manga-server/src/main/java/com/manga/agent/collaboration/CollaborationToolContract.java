package com.manga.agent.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.capability.CapabilityContractSupport;
import com.manga.agent.capability.CapabilityDescriptor;
import com.manga.agent.capability.CapabilityName;
import com.manga.agent.domain.AgentPermissionPolicy;

/** 工具与冻结 Worker Blueprint 共用的协议定义，避免编译器和能力注册表形成依赖环。 */
public final class CollaborationToolContract {
    /** 委派协议同时支持已发布配置身份与临时只读声明的语义版本。 */
    public static final int DELEGATION_VERSION = 3;
    /** 结果提交和读取协议未改变，不随委派协议一同升级。 */
    public static final int RESULT_VERSION = 1;
    /** 每次恢复后允许的最大领取次数。 */
    public static final int RETRY_ATTEMPTS = 3;
    /** 系统汇合节点名，不接受模型指定。 */
    public static final String JOIN_NODE_KEY = "all_success_join";
    /** 注入单条结果摘要的最大字符数，完整结果通过工具读取。 */
    public static final int SUMMARY_CHARACTERS = 320;
    /** 摘要最多携带的评审问题数。 */
    public static final int SUMMARY_ISSUES = 2;
    /** 每条评审问题的摘要字符数。 */
    public static final int ISSUE_CHARACTERS = 120;
    /** 摘要最多携带的产物引用数。 */
    public static final int SUMMARY_ARTIFACTS = 4;
    /** 单次恢复注入的协作 JSON 上限，原始用户输入仍由会话上下文策略控制。 */
    public static final int CONTEXT_CHARACTERS = 16000;
    private static final String PLAN_SCHEMA = """
        {"type":"object","additionalProperties":false,"properties":{"title":{"type":"string","maxLength":120},"nodes":{"type":"array","minItems":1,"maxItems":16,"items":{"type":"object","additionalProperties":false,"properties":{"key":{"type":"string","pattern":"^[a-z][a-z0-9_]{0,47}$"},"profile":{"type":"string","pattern":"^[a-z][a-z0-9_]{2,63}$"},"dynamicAgent":{"type":"object","additionalProperties":false,"properties":{"name":{"type":"string","maxLength":120},"reason":{"type":"string","maxLength":1000},"role":{"type":"string","enum":["script_parser","script_writer","storyboard_director","continuity_reviewer"]},"systemPrompt":{"type":"string","maxLength":6000},"capabilities":{"type":"array","maxItems":4,"uniqueItems":true,"items":{"type":"string","enum":["get_project_overview","get_script_structure","get_chapter_content","list_storyboard_shots"]}},"maxTurns":{"type":"integer","minimum":1,"maximum":8}},"required":["name","reason","role","systemPrompt","capabilities","maxTurns"]},"instruction":{"type":"string","maxLength":12000},"dependsOn":{"type":"array","items":{"type":"string"}}},"required":["key","instruction","dependsOn"],"oneOf":[{"required":["profile"],"not":{"required":["dynamicAgent"]}},{"required":["dynamicAgent"],"not":{"required":["profile"]}}]}}},"required":["title","nodes"]}
        """;
    private static final String RESULT_SCHEMA = """
        {"type":"object","properties":{"kind":{"type":"string","enum":["ANALYSIS","SCRIPT_DRAFT","STORYBOARD_DRAFT","REVIEW"]},"content":{"type":"string","maxLength":2000},"candidate":{"type":["object","null"]},"verdict":{"type":["string","null"],"enum":["PASS","NEEDS_REPAIR",null]},"issues":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":500}},"artifactIds":{"type":"array","maxItems":16,"items":{"type":"integer"}}},"required":["kind","content","candidate","verdict","issues","artifactIds"],"additionalProperties":false}
        """;
    private static final String READ_SCHEMA = """
        {"type":"object","properties":{"stepId":{"type":"string","maxLength":36}},"required":["stepId"],"additionalProperties":false}
        """;

    /** 同一描述同时用于工具注册和 Worker 能力指纹。 */
    public static CapabilityDescriptor descriptor(ObjectMapper mapper, CapabilityName name) {
        String description = switch (name) {
            case DELEGATE_CREATIVE_PLAN -> "委派一批持久化 Worker DAG。profile 使用已发布标识，或使用 dynamicAgent 声明临时只读 Worker（二选一）。临时规格必须说明 reason，只能使用父权限内的读取能力，不能指定模型、连接或递归委派。必须且只能有一个角色为 continuity_reviewer 的评审节点，可使用已发布配置或临时声明，其传递依赖必须覆盖其他所有节点。提交后停止本次推理，运行时会释放资源并在汇合后恢复。";
            case SUBMIT_CREATIVE_STEP_RESULT -> "Worker 提交不可变结果并结束。SCRIPT_DRAFT candidate 使用 {chapterId,title,synopsis,rawContent,scenes}；STORYBOARD_DRAFT 使用 {chapterId,shots}，每个 shot 含 title,sceneName,shotType,cameraMovement,durationSeconds,content,dialogue,soundEffect,notes；REVIEW 必须 verdict 与 issues；其余 candidate/verdict 为 null。";
            case READ_DELEGATED_RESULT -> "按 stepId 读取本次 Run 内已完成的结果。Worker 只能读取自己的前置依赖；Director 可读取所有子步骤。";
            default -> throw new IllegalArgumentException(CollaborationMessages.INVALID_PLAN);
        };
        String schema = switch (name) {
            case DELEGATE_CREATIVE_PLAN -> PLAN_SCHEMA;
            case SUBMIT_CREATIVE_STEP_RESULT -> RESULT_SCHEMA;
            case READ_DELEGATED_RESULT -> READ_SCHEMA;
            default -> throw new IllegalArgumentException(CollaborationMessages.INVALID_PLAN);
        };
        int version = name == CapabilityName.DELEGATE_CREATIVE_PLAN ? DELEGATION_VERSION : RESULT_VERSION;
        CapabilityDescriptor base = CapabilityContractSupport.readOnlyDescriptor(mapper, name, description, schema, version);
        boolean readOnly = name == CapabilityName.READ_DELEGATED_RESULT;
        return new CapabilityDescriptor(name, description, base.inputSchema(), base.schemaSha256(),
                readOnly, readOnly, AgentPermissionPolicy.READ_ONLY, version);
    }

    private CollaborationToolContract() { }
}
