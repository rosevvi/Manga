package com.manga.agent.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.entity.MangaProject;
import com.manga.repository.ProjectRepository;

import java.util.Objects;

/**
 * 为阶段二只读能力统一执行项目绑定和访问权限校验。
 */
abstract class AbstractProjectReadCapability implements MangaAgentCapability {

    protected final ProjectRepository projectRepository;
    protected final ObjectMapper objectMapper;

    protected AbstractProjectReadCapability(ProjectRepository projectRepository, ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    /** 返回当前用户可访问的绑定项目，避免信任模型输入中的项目主键。 */
    protected MangaProject requireAccessibleProject(AgentRuntimeContext context) {
        if (Objects.isNull(context.projectId())) {
            throw new IllegalStateException(CreativeRuntimeMessages.CAPABILITY_PROJECT_REQUIRED);
        }
        return projectRepository.findAccessibleById(context.projectId(), context.userId())
                .orElseThrow(() -> new IllegalArgumentException(
                        CreativeRuntimeMessages.CAPABILITY_RESOURCE_UNAVAILABLE));
    }

    /** 将已筛选的领域投影转换为结构化工具结果。 */
    protected CapabilityResult result(Object projection) {
        JsonNode data = objectMapper.valueToTree(projection);
        return new CapabilityResult(data);
    }
}
