package com.manga.repository;

import com.manga.entity.MangaProject;
import com.manga.mapper.ProjectRevisionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 封装项目版本锁及条件更新。 */
@Repository
@RequiredArgsConstructor
public class ProjectRevisionRepository {
    private final ProjectRevisionMapper mapper;
    /** 锁定所有者项目，缺失与无权限统一返回空。 */
    public Optional<MangaProject> lockOwned(long projectId, long userId) { return Optional.ofNullable(mapper.lockOwned(projectId, userId)); }
    /** CAS 推进版本。 */
    public boolean advanceProject(long projectId, long revision, String actor) { return mapper.advanceProject(projectId, revision, actor) == 1; }
    /** 推进所属章节版本。 */
    public boolean advanceChapter(long projectId, long chapterId, String actor) { return mapper.advanceChapter(projectId, chapterId, actor) == 1; }
}
