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

    public List<MangaProject> findAllOwnedBy(long ownerUserId) {
        return projectMapper.findAllOwnedBy(ownerUserId);
    }

    public Optional<MangaProject> findOwnedById(long projectId, long ownerUserId) {
        return Optional.ofNullable(projectMapper.findOwnedById(projectId, ownerUserId));
    }

    public MangaProject create(MangaProject project) {
        projectMapper.insert(project);
        if (project.getId() == null) {
            throw new IllegalStateException(ExceptionMessageConstants.GENERATED_PROJECT_ID_UNAVAILABLE);
        }
        return project;
    }

    public void update(MangaProject project, long ownerUserId) {
        projectMapper.updateOwned(project, ownerUserId);
    }

    public void delete(long projectId) {
        projectMapper.deleteById(projectId);
    }
}
