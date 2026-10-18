package com.manga.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.common.enums.ScriptChapterParseStatus;
import com.manga.entity.ProjectScript;
import com.manga.entity.ProjectScriptChapter;
import com.manga.repository.ProjectRepository;
import com.manga.repository.ProjectScriptChapterRepository;
import com.manga.repository.ProjectScriptRepository;
import org.springframework.stereotype.Component;

/**
 * 向 Director 提供当前项目内指定章节的正文与解析结构。
 */
@Component
public class GetChapterContentCapability extends AbstractProjectReadCapability {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{"chapterId":{"type":"integer","minimum":1}},"required":["chapterId"],"additionalProperties":false}
            """;
    private final ProjectScriptRepository scriptRepository;
    private final ProjectScriptChapterRepository chapterRepository;
    private final CapabilityDescriptor descriptor;

    public GetChapterContentCapability(
            ProjectRepository projectRepository,
            ProjectScriptRepository scriptRepository,
            ProjectScriptChapterRepository chapterRepository,
            ObjectMapper objectMapper) {
        super(projectRepository, objectMapper);
        this.scriptRepository = scriptRepository;
        this.chapterRepository = chapterRepository;
        descriptor = CapabilityContractSupport.readOnlyDescriptor(
                objectMapper,
                CapabilityName.GET_CHAPTER_CONTENT,
                "读取当前项目内一个剧本章节的标题、简介、正文和结构化解析结果。",
                INPUT_SCHEMA,
                1
        );
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return descriptor;
    }

    /** 校验章节属于当前已授权项目后返回正文。 */
    @Override
    public CapabilityResult execute(CapabilityCommand command, AgentRuntimeContext context) {
        long projectId = requireAccessibleProject(context).getId();
        long chapterId = CapabilityContractSupport.requirePositiveLong(
                command, CapabilityContractSupport.CHAPTER_ID_FIELD);
        ProjectScript script = scriptRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalArgumentException(
                        CreativeRuntimeMessages.CAPABILITY_RESOURCE_UNAVAILABLE));
        ProjectScriptChapter chapter = chapterRepository.findByIdAndScript(chapterId, script.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        CreativeRuntimeMessages.CAPABILITY_RESOURCE_UNAVAILABLE));
        return result(new ChapterContent(
                chapter.getId(),
                chapter.getTitle(),
                chapter.getSynopsis(),
                chapter.getRawContent(),
                chapter.getStructureJson(),
                chapter.getParseStatus(),
                chapter.getStructureVersion()
        ));
    }

    /** Director 可见的章节正文投影。 */
    private record ChapterContent(
            /** 章节主键。 */ Long id,
            /** 章节标题。 */ String title,
            /** 章节简介。 */ String synopsis,
            /** 原始章节正文。 */ String rawContent,
            /** 解析后的结构化 JSON。 */ String structureJson,
            /** 章节解析状态。 */ ScriptChapterParseStatus parseStatus,
            /** 结构化数据版本。 */ Integer structureVersion
    ) {
    }
}
