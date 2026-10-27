package com.manga.service;

import com.manga.agent.changeset.ChangeSetResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.entity.MangaProject;
import com.manga.entity.ProjectScriptChapter;
import com.manga.repository.ProjectRevisionRepository;
import com.manga.repository.ProjectScriptChapterRepository;
import com.manga.repository.ProjectScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 项目内容聚合的版本门；手动编辑和审批写入使用相同锁顺序。 */
@Service
@RequiredArgsConstructor
public class ProjectRevisionService {
    private final ProjectRevisionRepository revisions;
    private final ProjectScriptRepository scripts;
    private final ProjectScriptChapterRepository chapters;

    /** 在调用者事务内锁住项目；不推进版本，供生成快照和审批冲突校验。 */
    @Transactional
    public MangaProject lock(long projectId, long userId) {
        return revisions.lockOwned(projectId, userId)
                .orElseThrow(() -> new BusinessException(ChangeSetResponseCode.NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /** 验证章节归属并读取正文和 revision；调用方须先持有项目锁。 */
    @Transactional(readOnly = true)
    public ProjectScriptChapter chapter(long projectId, long chapterId) {
        return scripts.findByProjectId(projectId)
                .flatMap(script -> chapters.findByIdAndScript(chapterId, script.getId()))
                .orElseThrow(() -> new BusinessException(ChangeSetResponseCode.NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /** 审批事务中的乐观校验：先锁项目再读取章节，不存在或版本变化均返回冲突。 */
    @Transactional
    public boolean matches(long projectId, long userId, long expectedProject, Long chapterId, Long expectedChapter) {
        MangaProject project = revisions.lockOwned(projectId, userId).orElse(null);
        if (Objects.isNull(project) || !Objects.equals(project.getRevision(), expectedProject)) return false;
        if (Objects.isNull(chapterId)) return true;
        return scripts.findByProjectId(projectId).flatMap(script -> chapters.findByIdAndScript(chapterId, script.getId()))
                .filter(chapter -> Objects.equals(chapter.getRevision(), expectedChapter)).isPresent();
    }

    /** 在与内容写入相同的事务中推进项目和可选章节版本，后续失败自动回滚。 */
    @Transactional
    public void edit(long projectId, long userId, Long chapterId, String actor) {
        MangaProject project = lock(projectId, userId);
        if (!revisions.advanceProject(projectId, project.getRevision(), actor)) {
            throw new BusinessException(ChangeSetResponseCode.REVISION_CONFLICT, HttpStatus.CONFLICT);
        }
        if (Objects.nonNull(chapterId) && !revisions.advanceChapter(projectId, chapterId, actor)) {
            throw new BusinessException(ChangeSetResponseCode.NOT_FOUND, HttpStatus.NOT_FOUND);
        }
    }
}
