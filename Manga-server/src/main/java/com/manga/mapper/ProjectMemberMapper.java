package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.ProjectMember;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 定义项目成员及其用户公开摘要的数据访问契约。 */
public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    /** 查询项目成员列表。 */
    List<ProjectMember> findMembersByProject(@Param("projectId") long projectId);

    /** 查询指定项目成员。 */
    ProjectMember findMember(@Param("projectId") long projectId, @Param("userId") long userId);
}
