package com.manga.repository;

import com.manga.entity.ProjectWorkflow;
import com.manga.mapper.ProjectWorkflowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 使用 MyBatis-Plus 持久化项目工作流阶段。 */
@Repository
@RequiredArgsConstructor
public class ProjectWorkflowRepository {

    private final ProjectWorkflowMapper mapper;

    /** 按项目查询工作流阶段。 */
    public Optional<ProjectWorkflow> findByProjectId(long projectId) {
        return Optional.ofNullable(mapper.findByProjectId(projectId));
    }

    /** 创建工作流阶段记录。 */
    public ProjectWorkflow create(ProjectWorkflow workflow) {
        mapper.insert(workflow);
        return workflow;
    }

    /** 更新工作流阶段。 */
    public void updateStage(ProjectWorkflow workflow) {
        mapper.updateStage(workflow);
    }
}
