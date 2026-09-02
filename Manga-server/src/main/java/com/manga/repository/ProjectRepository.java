package com.manga.repository;

import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.entity.MangaProject;
import com.manga.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 使用 MyBatis-Plus 持久化项目并限制所有者访问。 */
@Repository
@RequiredArgsConstructor
public class ProjectRepository {

    private final ProjectMapper projectMapper;

    /** 查询用户拥有的项目列表。 */
    public List<MangaProject> findAllOwnedBy(long ownerUserId) {
        return projectMapper.findAllOwnedBy(ownerUserId);
    }

    /** 查询用户可访问的项目列表。 */
    public List<MangaProject> findAllAccessibleBy(long userId) {
        return projectMapper.findAllAccessibleBy(userId);
    }

    /** 按 ID 查询用户拥有的项目。 */
    public Optional<MangaProject> findOwnedById(long projectId, long ownerUserId) {
        return Optional.ofNullable(projectMapper.findOwnedById(projectId, ownerUserId));
    }

    /** 按 ID 查询用户可访问的项目。 */
    public Optional<MangaProject> findAccessibleById(long projectId, long userId) {
        return Optional.ofNullable(projectMapper.findAccessibleById(projectId, userId));
    }

    /** 创建项目。 */
    public MangaProject create(MangaProject project) {
        projectMapper.insert(project);
        if (project.getId() == null) {
            throw new IllegalStateException(ExceptionMessageConstants.GENERATED_PROJECT_ID_UNAVAILABLE);
        }
        return project;
    }

    /** 更新项目。 */
    public void update(MangaProject project, long ownerUserId) {
        projectMapper.updateOwned(project, ownerUserId);
    }

    /** 删除项目。 */
    public void delete(long projectId) {
        projectMapper.deleteById(projectId);
    }
}
