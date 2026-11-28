package com.manga.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.application.ChapterAssetExtraction;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.domain.AgentPermissionPolicy;
import org.springframework.stereotype.Component;

/** 工具输入只包含识别结果，写入目标完全来自 Run。 */
@Component
public class RegisterChapterAssetsCapability implements MangaAgentCapability {
    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["revision","synopsis","assets"],"properties":{
              "revision":{"type":"integer","minimum":1},"synopsis":{"type":"string","minLength":1,"maxLength":2000},
              "assets":{"type":"array","maxItems":%d,"items":{"type":"object","additionalProperties":false,
                "required":["assetKind","fields"],"properties":{"assetKind":{"type":"string","enum":["CHARACTER","SCENE","PROP"]},
                "existingAssetId":{"type":"integer","minimum":1},"fields":{"type":"object","additionalProperties":false,
                "required":["displayName"],"properties":{"displayName":{"type":"string","minLength":1,"maxLength":120},
                "overview":{"type":"string","maxLength":2000},"roleLabel":{"type":"string","maxLength":40},
                "identityDescription":{"type":"string","maxLength":300},"ageGroup":{"type":"string","maxLength":40},
                "appearanceDescription":{"type":"string","maxLength":800},"costumeDescription":{"type":"string","maxLength":800},
                "locationDescription":{"type":"string","maxLength":300},"environmentDescription":{"type":"string","maxLength":800},
                "lightingDescription":{"type":"string","maxLength":300},"materialDescription":{"type":"string","maxLength":300}}}}}}}}
            """.formatted(ChapterAssetExtraction.MAX_ASSETS);
    private final ChapterAssetExtraction extraction;
    private final ChangeSetCodec codec;
    private final ObjectMapper mapper;
    private final CapabilityDescriptor descriptor;

    public RegisterChapterAssetsCapability(ChapterAssetExtraction extraction, ChangeSetCodec codec, ObjectMapper mapper) {
        this.extraction = extraction;
        this.codec = codec;
        this.mapper = mapper;
        String description = "一次提交整章摘要和全部资产。角色、场景、道具分别使用对应字段。有明确匹配时填 existingAssetId，已有资料会保留。";
        var schema = CapabilityContractSupport.readOnlyDescriptor(mapper, CapabilityName.REGISTER_CHAPTER_ASSETS, description, SCHEMA, 1);
        descriptor = new CapabilityDescriptor(schema.name(), description, schema.inputSchema(), schema.schemaSha256(),
                false, false, AgentPermissionPolicy.AUTOMATION, 1);
    }

    @Override public CapabilityDescriptor descriptor() { return descriptor; }

    @Override public CapabilityResult execute(CapabilityCommand command, AgentRuntimeContext context) {
        var input = codec.validateForTool(command.input(), ChapterAssetExtraction.Submission.class);
        String receipt = extraction.register(context, input);
        return new CapabilityResult(mapper.valueToTree(receipt), new CapabilitySummary("章节资产", receipt));
    }
}
