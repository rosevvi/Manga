package com.manga.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.enums.ScriptChapterParseStatus;
import com.manga.entity.ProjectScript;
import com.manga.entity.ProjectScriptChapter;
import com.manga.repository.ProjectRepository;
import com.manga.repository.ProjectScriptChapterRepository;
import com.manga.repository.ProjectScriptRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 向 Director 提供剧本元数据与章节目录。
 */
@Component
public class GetScriptStructureCapability extends AbstractProjectReadCapability {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{},"additionalProperties":false}
            """;
    private final ProjectScriptRepository scriptRepository;
    private final ProjectScriptChapterRepository chapterRepository;
    private final CapabilityDescriptor descriptor;

    public GetScriptStructureCapability(
            ProjectRepository projectRepository,
            ProjectScriptRepository scriptRepository,
            ProjectScriptChapterRepository chapterRepository,
            ObjectMapper objectMapper) {
        super(projectRepository, objectMapper);
        this.scriptRepository = scriptRepository;
        this.chapterRepository = chapterRepository;
        descriptor = CapabilityContractSupport.readOnlyDescriptor(
                objectMapper,
                CapabilityName.GET_SCRIPT_STRUCTURE,
                "读取当前项目的剧本标题、简介、解析状态和章节目录，不返回完整章节正文。",
                INPUT_SCHEMA,
                1
        );
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return descriptor;
    }

    /** 返回剧本目录；项目尚无剧本时返回 exists=false。 */
    @Override
    public CapabilityResult execute(CapabilityCommand command, AgentRuntimeContext context) {
        long projectId = requireAccessibleProject(context).getId();
        ProjectScript script = scriptRepository.findByProjectId(projectId).orElse(null);
        if (Objects.isNull(script)) {
            return result(new ScriptStructure(false, null, null, null, 0, List.of()));
        }
        List<ChapterSummary> chapters = chapterRepository.findSummaries(script.getId()).stream()
                .map(chapter -> new ChapterSummary(
                        chapter.getId(), chapter.getTitle(), chapter.getSynopsis(),
                        chapter.getSortOrder(), chapter.getParseStatus(), chapter.getRawContentLength()))
                .toList();
        return result(new ScriptStructure(
                true,
                script.getTitle(),
                script.getSynopsis(),
                script.getParseStatus(),
                script.getChapterCount(),
                chapters
        ));
    }

    /** Director 可见的剧本结构投影。 */
    private record ScriptStructure(
            /** 当前项目是否已有剧本。 */ boolean exists,
            /** 剧本标题。 */ String title,
            /** 剧本简介。 */ String synopsis,
            /** 整体解析状态。 */ String parseStatus,
            /** 章节数量。 */ Integer chapterCount,
            /** 按创作顺序排列的章节目录。 */ List<ChapterSummary> chapters
    ) {
    }

    /** Director 可见的章节目录项。 */
    private record ChapterSummary(
            /** 章节主键。 */ Long id,
            /** 章节标题。 */ String title,
            /** 章节简介。 */ String synopsis,
            /** 章节排序值。 */ Integer sortOrder,
            /** 章节解析状态。 */ ScriptChapterParseStatus parseStatus,
            /** 原始正文字符数。 */ Integer contentLength
    ) {
    }
}
