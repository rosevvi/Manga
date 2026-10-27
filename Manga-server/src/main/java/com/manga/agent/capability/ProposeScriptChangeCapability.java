package com.manga.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.agent.changeset.ChangeSetProposalService;
import org.springframework.stereotype.Component;

/** 提交新章节或章节改写提案，正文和场景作为整体审批。 */
@Component
public class ProposeScriptChangeCapability extends AbstractChangeSetCapability {
    /** 与 ScriptChangeSet v1 对齐；归属和真实版本由服务端决定。 */
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{
              "chapterId":{"type":["integer","null"],"minimum":1},
              "title":{"type":"string","minLength":1,"maxLength":120},
              "synopsis":{"type":["string","null"],"maxLength":2000},
              "rawContent":{"type":"string","minLength":1,"maxLength":200000},
              "scenes":{"type":"array","maxItems":200,"items":{"type":"object","additionalProperties":false,
                "properties":{"sceneNumber":{"type":"integer"},"location":{"type":"string","maxLength":120},
                  "time":{"type":"string","maxLength":120},"summary":{"type":"string","maxLength":2000},
                  "content":{"type":"string","maxLength":5000},
                  "dialogues":{"type":"array","items":{"type":"object","additionalProperties":false,
                    "properties":{"speaker":{"type":"string","maxLength":120},"text":{"type":"string","maxLength":5000},
                      "type":{"type":"string","maxLength":20}}}}}}}
            },"required":["title","rawContent","scenes"]}
            """;

    /** 注册不会直接写入业务表的提案工具。 */
    public ProposeScriptChangeCapability(ChangeSetProposalService service, ObjectMapper mapper) {
        super(service, mapper, ChangeSetKind.SCRIPT, CapabilityName.PROPOSE_SCRIPT_CHANGE,
                "提交一个章节的新建或完整改写提案，chapterId 为空表示新建。提交后必须等待用户审批，不能声称已经保存。", SCHEMA);
    }
}
