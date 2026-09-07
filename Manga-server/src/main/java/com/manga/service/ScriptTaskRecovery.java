package com.manga.service;

import com.manga.common.enums.TaskStatus;
import com.manga.entity.MangaTask;
import com.manga.repository.MangaTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 应用启动后恢复未完成的章节导入任务。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScriptTaskRecovery {

    private final MangaTaskRepository taskRepository;
    private final ScriptTaskService taskService;

    /** 扫描并重新提交未完成任务。 */
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        taskRepository.findRecoverable().stream()
                .filter(task -> task.getStatus() == TaskStatus.QUEUED || task.getStatus() == TaskStatus.RUNNING)
                .forEach(taskService::resume);
        log.info("Script import task recovery scan completed");
    }
}
