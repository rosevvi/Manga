package com.manga.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.application.ChapterAssetExtraction;
import com.manga.agent.changeset.ChangeSetCodec;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.stereotype.Component;

/** 向识别 Agent 提供本次任务绑定的章节原文和已有资产目录。 */
@Component
public class ReadChapterAssetContextCapability implements MangaAgentCapability {
    private final ChapterAssetExtraction extraction;
    private final ObjectMapper mapper;
    private final ChangeSetCodec codec;
    private final CapabilityDescriptor descriptor;

    public ReadChapterAssetContextCapability(ChapterAssetExtraction extraction, ObjectMapper mapper, ChangeSetCodec codec) {
        this.extraction = extraction;
        this.mapper = mapper;
        this.codec = codec;
        descriptor = CapabilityContractSupport.readOnlyDescriptor(mapper, CapabilityName.READ_CHAPTER_ASSET_CONTEXT,
                "读取当前任务的章节正文、revision 和项目资产目录。首次从 0 开始，继续读取时传入 nextOffset。",
                """
                {"type":"object","properties":{"offset":{"type":"integer","minimum":0}},"additionalProperties":false}
                """, 1);
    }

    @Override public CapabilityDescriptor descriptor() { return descriptor; }

    @Override public CapabilityResult execute(CapabilityCommand command, AgentRuntimeContext context) {
        var page = codec.validateForTool(command.input(), Page.class);
        var result = extraction.read(context, page.offset() == null ? 0 : page.offset());
        return new CapabilityResult(mapper.valueToTree(result), new CapabilitySummary("章节原文", result.title()));
    }

    private record Page(@PositiveOrZero Integer offset) {}
}
