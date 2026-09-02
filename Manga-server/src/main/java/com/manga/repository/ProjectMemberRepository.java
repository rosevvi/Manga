package com.manga.repository;

import com.manga.entity.ProjectMember;
import com.manga.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 使用 MyBatis-Plus 持久化项目成员关系。 */
@Repository
@RequiredArgsConstructor
public class ProjectMemberRepository {

    private final ProjectMemberMapper projectMemberMapper;

    /** 创建项目成员。 */
    public ProjectMember create(ProjectMember member) {
        projectMemberMapper.insert(member);
        return member;
    }

    /** 查询项目成员列表。 */
    public List<ProjectMember> findMembersByProject(long projectId) {
        return projectMemberMapper.findMembersByProject(projectId);
    }

    /** 查询指定项目成员。 */
    public Optional<ProjectMember> findMember(long projectId, long userId) {
        return Optional.ofNullable(projectMemberMapper.findMember(projectId, userId));
    }
}
