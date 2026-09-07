package com.manga.repository;

import com.manga.entity.ProjectScript;
import com.manga.mapper.ProjectScriptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 使用 MyBatis-Plus 持久化剧本元数据。 */
@Repository
@RequiredArgsConstructor
public class ProjectScriptRepository {

    private final ProjectScriptMapper mapper;

    public Optional<ProjectScript> findByProjectId(long projectId) {
        return Optional.ofNullable(mapper.findByProjectId(projectId));
    }

    public ProjectScript create(ProjectScript script) {
        mapper.insert(script);
        return script;
    }

    public void update(ProjectScript script) {
        mapper.updateByProjectId(script);
    }

    public void updateChapterCount(long scriptId, int chapterCount) {
        mapper.updateChapterCount(scriptId, chapterCount);
    }
}
