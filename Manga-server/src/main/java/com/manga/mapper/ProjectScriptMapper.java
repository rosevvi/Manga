package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.ProjectScript;
import org.apache.ibatis.annotations.Param;

/** 定义项目剧本原文和结构化结果的数据访问契约。 */
public interface ProjectScriptMapper extends BaseMapper<ProjectScript> {

    /** 按项目查询剧本。 */
    ProjectScript findByProjectId(@Param("projectId") long projectId);

    /** 更新项目剧本。 */
    int updateByProjectId(@Param("script") ProjectScript script);
}
