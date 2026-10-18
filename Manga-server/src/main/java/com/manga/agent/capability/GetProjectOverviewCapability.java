package com.manga.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.enums.ProjectStatus;
import com.manga.common.enums.ProjectWorkflowStage;
import com.manga.entity.MangaProject;
import com.manga.repository.ProjectRepository;
import org.springframework.stereotype.Component;

/**
 * 向 Director 提供当前项目的安全只读概览。
 */
@Component
public class GetProjectOverviewCapability extends AbstractProjectReadCapability {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{},"additionalProperties":false}
            """;
    private final CapabilityDescriptor descriptor;

    public GetProjectOverviewCapability(ProjectRepository projectRepository, ObjectMapper objectMapper) {
        super(projectRepository, objectMapper);
        descriptor = CapabilityContractSupport.readOnlyDescriptor(
                objectMapper,
                CapabilityName.GET_PROJECT_OVERVIEW,
                "读取当前漫剧项目的名称、题材、画幅、画风、状态和创作阶段。",
                INPUT_SCHEMA,
                1
        );
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return descriptor;
    }

    /** 仅从认证上下文读取项目，不接受模型提供的项目主键。 */
    @Override
    public CapabilityResult execute(CapabilityCommand command, AgentRuntimeContext context) {
        MangaProject project = requireAccessibleProject(context);
        return result(new ProjectOverview(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getGenre(),
                project.getAspectRatio(),
                project.getArtStyle(),
                project.getArtStyleDescription(),
                project.getStatus(),
                project.getWorkflowStage(),
                project.getShotCount()
        ));
    }

    /** Director 可见的项目概览投影。 */
    private record ProjectOverview(
            /** 项目主键。 */ Long id,
            /** 项目名称。 */ String name,
            /** 项目简介。 */ String description,
            /** 项目题材。 */ String genre,
            /** 默认画面比例。 */ String aspectRatio,
            /** 画风预设。 */ String artStyle,
            /** 自定义画风说明。 */ String artStyleDescription,
            /** 项目状态。 */ ProjectStatus status,
            /** 当前创作阶段。 */ ProjectWorkflowStage workflowStage,
            /** 已创建分镜数量。 */ Integer shotCount
    ) {
    }
}
