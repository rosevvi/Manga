package com.manga.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.agent.changeset.ChangeSetProposalService;
import org.springframework.stereotype.Component;

/** 提交单章节批量分镜草稿，仅允许追加。 */
@Component
public class ProposeStoryboardChangeCapability extends AbstractChangeSetCapability {
    /** 与 StoryboardChangeSet v1 对齐，禁止指定媒体或完成状态。 */
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{
              "chapterId":{"type":"integer","minimum":1},
              "shots":{"type":"array","minItems":1,"maxItems":100,"items":{"type":"object","additionalProperties":false,
                "properties":{"title":{"type":"string","minLength":1,"maxLength":120},
                  "sceneName":{"type":"string","maxLength":120},"shotType":{"type":"string","maxLength":32},
                  "cameraMovement":{"type":"string","maxLength":64},
                  "durationSeconds":{"type":"integer","minimum":0,"maximum":3600},
                  "content":{"type":"string","maxLength":2000},"dialogue":{"type":"string","maxLength":2000},
                  "soundEffect":{"type":"string","maxLength":500},"notes":{"type":"string","maxLength":2000}},
                "required":["title"]}}
            },"required":["chapterId","shots"]}
            """;

    /** 注册只追加草稿、等待审批的分镜提案能力。 */
    public ProposeStoryboardChangeCapability(ChangeSetProposalService service, ObjectMapper mapper) {
        super(service, mapper, ChangeSetKind.STORYBOARD, CapabilityName.PROPOSE_STORYBOARD_CHANGE,
                "向当前项目的指定章节批量追加分镜草稿提案，不删除已有镜头、不生成媒体；提交后等待用户审批。", SCHEMA);
    }
}
