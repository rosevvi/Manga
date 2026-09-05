package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.ProjectWorkflow;
import org.apache.ibatis.annotations.Param;

/** 定义项目工作流阶段的数据访问契约。 */
public interface ProjectWorkflowMapper extends BaseMapper<ProjectWorkflow> {

    /** 按项目查询工作流阶段。 */
    ProjectWorkflow findByProjectId(@Param("projectId") long projectId);

    /** 更新项目当前工作流阶段。 */
    int updateStage(@Param("workflow") ProjectWorkflow workflow);
}
