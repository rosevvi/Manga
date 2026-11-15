package com.manga.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.agent.changeset.ChangeSetProposalService;
import org.springframework.stereotype.Component;

/** 图片提案只声明镜头和输入，由平台审批并提交生成。 */
@Component
public class ProposeImageGenerationCapability extends AbstractChangeSetCapability {
    /** 生成参数按服务端 Schema 校验。 */
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{
              "shotId":{"type":"integer","minimum":1},
              "provider":{"type":"string","enum":["FAKE","OPENAI","AGNES"]},
              "configId":{"type":["integer","null"],"minimum":1},
              "model":{"type":"string","minLength":1,"maxLength":100},
              "prompt":{"type":"string","minLength":1,"maxLength":4000},
              "width":{"type":"integer","minimum":512,"maximum":2048},
              "height":{"type":"integer","minimum":512,"maximum":2048}
            },"required":["shotId","provider","model","prompt","width","height"]}
            """;
    /** 注册由平台审批策略处理的图片生成提案工具。 */
    public ProposeImageGenerationCapability(ChangeSetProposalService service, ObjectMapper mapper) {
        super(service, mapper, ChangeSetKind.IMAGE_GENERATION, CapabilityName.PROPOSE_IMAGE_GENERATION,
                "为当前项目镜头提出单张图片生成提案。OpenAI 和 Agnes 必须使用用户选择的 configId、model 和尺寸，不得猜测配置 ID。FAKE 仅用于流程验证。提案提交后，由平台按本轮审批方式处理生成。", SCHEMA);
    }
}
