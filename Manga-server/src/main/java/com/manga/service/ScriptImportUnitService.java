package com.manga.service;

import com.manga.common.enums.TaskStatus;
import com.manga.dto.ProjectScriptImportRequest;
import com.manga.entity.MangaTask;
import com.manga.entity.MangaTaskUnit;
import com.manga.repository.MangaTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/** 将文件中的一个章节、该章的识别任务和导入进度作为一个事务提交。 */
@Service
@RequiredArgsConstructor
public class ScriptImportUnitService {
    private final ProjectScriptService scripts;
    private final MangaTaskRepository tasks;
    private final ProjectRevisionService revisions;

    /** 在项目锁内复查导入序号，恢复任务时不会重复创建章节或触发 Agent。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MangaTaskUnit importUnit(MangaTask task, int index, ProjectScriptImportRequest.Chapter input, Long afterChapterId) {
        long projectId = task.getProjectId(), userId = task.getOwnerUserId();
        revisions.lock(projectId, userId);
        var existing = tasks.findUnit(task.getId(), index);
        if (existing.isPresent()) return existing.get();
        String actor = task.getUpdatedBy();
        int order = scripts.prepareInsertOrder(projectId, afterChapterId, 1, userId, actor);
        var chapter = scripts.createTaskChapter(projectId, input.title(), input.synopsis(), input.rawContent(),
                task.getSourceContentType(), order, userId, actor);
        var now = LocalDateTime.now();
        return tasks.createUnit(MangaTaskUnit.builder().taskId(task.getId()).unitIndex(index).chapterId(chapter.getId())
                .chapterTitle(input.title()).status(TaskStatus.COMPLETED).startedAt(now).finishedAt(now).build());
    }
}
