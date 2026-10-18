package com.manga.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.enums.StoryboardShotStatus;
import com.manga.entity.StoryboardShot;
import com.manga.repository.ProjectRepository;
import com.manga.repository.StoryboardShotRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向 Director 提供当前项目或指定章节的分镜列表。
 */
@Component
public class ListStoryboardShotsCapability extends AbstractProjectReadCapability {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{"chapterId":{"type":["integer","null"],"minimum":1}},"additionalProperties":false}
            """;
    private final StoryboardShotRepository shotRepository;
    private final CapabilityDescriptor descriptor;

    public ListStoryboardShotsCapability(
            ProjectRepository projectRepository,
            StoryboardShotRepository shotRepository,
            ObjectMapper objectMapper) {
        super(projectRepository, objectMapper);
        this.shotRepository = shotRepository;
        descriptor = CapabilityContractSupport.readOnlyDescriptor(
                objectMapper,
                CapabilityName.LIST_STORYBOARD_SHOTS,
                "读取当前项目全部分镜，或按章节筛选分镜镜头。",
                INPUT_SCHEMA,
                1
        );
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return descriptor;
    }

    /** 返回安全的分镜内容投影，不暴露审计字段。 */
    @Override
    public CapabilityResult execute(CapabilityCommand command, AgentRuntimeContext context) {
        long projectId = requireAccessibleProject(context).getId();
        Long chapterId = CapabilityContractSupport.optionalPositiveLong(
                command, CapabilityContractSupport.CHAPTER_ID_FIELD);
        List<ShotView> shots = shotRepository.findAllByProjectId(projectId, chapterId).stream()
                .map(this::toView)
                .toList();
        return result(new StoryboardShots(chapterId, shots.size(), shots));
    }

    private ShotView toView(StoryboardShot shot) {
        return new ShotView(
                shot.getId(), shot.getChapterId(), shot.getSortOrder(), shot.getShotNumber(),
                shot.getTitle(), shot.getSceneName(), shot.getShotType(), shot.getCameraMovement(),
                shot.getDurationSeconds(), shot.getContent(), shot.getDialogue(), shot.getSoundEffect(),
                shot.getImageUrl(), shot.getNotes(), shot.getStatus()
        );
    }

    /** Director 可见的分镜列表结果。 */
    private record StoryboardShots(
            /** 本次筛选的章节主键。 */ Long chapterId,
            /** 返回的镜头数量。 */ int count,
            /** 按项目时间线排序的镜头。 */ List<ShotView> shots
    ) {
    }

    /** Director 可见的单个分镜投影。 */
    private record ShotView(
            /** 分镜主键。 */ Long id,
            /** 所属章节主键。 */ Long chapterId,
            /** 项目时间线排序值。 */ Integer sortOrder,
            /** 分镜镜头编号。 */ String shotNumber,
            /** 分镜标题。 */ String title,
            /** 场景名称。 */ String sceneName,
            /** 景别或镜头类型。 */ String shotType,
            /** 镜头运动方式。 */ String cameraMovement,
            /** 镜头时长，单位为秒。 */ Integer durationSeconds,
            /** 画面内容。 */ String content,
            /** 对白。 */ String dialogue,
            /** 音效说明。 */ String soundEffect,
            /** 当前参考图地址。 */ String imageUrl,
            /** 制作备注。 */ String notes,
            /** 分镜制作状态。 */ StoryboardShotStatus status
    ) {
    }
}
