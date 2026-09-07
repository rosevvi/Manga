package com.manga.repository;

import com.manga.entity.ProjectScriptChapter;
import com.manga.mapper.ProjectScriptChapterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 使用 MyBatis-Plus 持久化章节正文和结构。 */
@Repository
@RequiredArgsConstructor
public class ProjectScriptChapterRepository {

    private final ProjectScriptChapterMapper mapper;

    public List<ProjectScriptChapter> findSummaries(long scriptId) {
        return mapper.findSummaries(scriptId);
    }

    public Optional<ProjectScriptChapter> findByIdAndScript(long chapterId, long scriptId) {
        return Optional.ofNullable(mapper.findByIdAndScript(chapterId, scriptId));
    }

    public ProjectScriptChapter create(ProjectScriptChapter chapter) {
        mapper.insert(chapter);
        return chapter;
    }

    public void update(ProjectScriptChapter chapter) {
        mapper.updateChapter(chapter);
    }

    public void shiftSortOrders(long scriptId, int sortOrder, int delta) {
        mapper.shiftSortOrders(scriptId, sortOrder, delta);
    }

    public void delete(long chapterId) {
        mapper.deleteById(chapterId);
    }
}
