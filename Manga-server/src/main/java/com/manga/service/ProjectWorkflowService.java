package com.manga.service;

import com.manga.common.enums.ProjectWorkflowStage;
import com.manga.entity.ProjectWorkflow;
import com.manga.repository.ProjectWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 管理项目创作工作流阶段及其推进规则。 */
@Service
@RequiredArgsConstructor
public class ProjectWorkflowService {

    private final ProjectWorkflowRepository workflowRepository;

    /** 获取项目工作流记录，不存在时创建初始记录。 */
    @Transactional
    public ProjectWorkflow getOrCreate(long projectId, String actor) {
        Optional<ProjectWorkflow> existing = workflowRepository.findByProjectId(projectId);
        if (existing.isPresent()) {
            return existing.get();
        }
        return workflowRepository.create(ProjectWorkflow.builder()
                .projectId(projectId)
                .currentStage(ProjectWorkflowStage.SCRIPT)
                .stageRevision(0)
                .createdBy(actor)
                .updatedBy(actor)
                .build());
    }

    /** 手动设置项目当前工作阶段。 */
    @Transactional
    public ProjectWorkflow setStage(long projectId, ProjectWorkflowStage stage, String actor) {
        ProjectWorkflow workflow = getOrCreate(projectId, actor);
        workflow.setCurrentStage(stage);
        workflow.setUpdatedBy(actor);
        workflowRepository.updateStage(workflow);
        return workflowRepository.findByProjectId(projectId).orElse(workflow);
    }

    /** 在产物完成后自动向前推进推荐阶段。 */
    @Transactional
    public ProjectWorkflow advanceTo(long projectId, ProjectWorkflowStage targetStage, String actor) {
        ProjectWorkflow workflow = getOrCreate(projectId, actor);
        if (targetStage.ordinal() <= workflow.getCurrentStage().ordinal()) {
            return workflow;
        }
        workflow.setCurrentStage(targetStage);
        workflow.setUpdatedBy(actor);
        workflowRepository.updateStage(workflow);
        return workflowRepository.findByProjectId(projectId).orElse(workflow);
    }
}
