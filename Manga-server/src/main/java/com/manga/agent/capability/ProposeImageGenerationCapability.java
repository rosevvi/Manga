package com.manga.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.agent.changeset.ChangeSetProposalService;
import org.springframework.stereotype.Component;

/** 图片提案只声明镜头和输入，服务端决定报价，不赋予模型付费执行权。 */
@Component
public class ProposeImageGenerationCapability extends AbstractChangeSetCapability {
    /** 费用由服务端生成，不进入模型工具参数。 */
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{
              "shotId":{"type":"integer","minimum":1},
              "provider":{"type":"string","enum":["FAKE","OPENAI"]},
              "configId":{"type":["integer","null"],"minimum":1},
              "model":{"type":"string","enum":["fake-image","gpt-image-2"]},
              "prompt":{"type":"string","minLength":1,"maxLength":4000},
              "width":{"type":"integer","enum":[1024,1536]},
              "height":{"type":"integer","enum":[1024,1536]}
            },"required":["shotId","provider","model","prompt","width","height"]}
            """;
    /** 注册待人工确认的图片生成提案工具。 */
    public ProposeImageGenerationCapability(ChangeSetProposalService service, ObjectMapper mapper) {
        super(service, mapper, ChangeSetKind.IMAGE_GENERATION, CapabilityName.PROPOSE_IMAGE_GENERATION,
                "为当前项目镜头提出单张图片生成提案。OpenAI 使用用户提供的 configId 和 gpt-image-2；不得猜测配置 ID。FAKE 仅用于免费流程验证。提交后等待用户确认费用及批准。", SCHEMA);
    }
}
