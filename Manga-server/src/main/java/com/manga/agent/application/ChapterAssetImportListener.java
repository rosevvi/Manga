package com.manga.agent.application;

import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceReference;
import com.manga.agent.domain.CreativeResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 同步登记持久化任务，由已有的事务提交后调度机制唤醒 Agent。 */
@Component
@RequiredArgsConstructor
public class ChapterAssetImportListener {
    private final RunAdmissionService admission;

    /** 自动导入以章节 ID 作为稳定幂等键，模型调用在工作线程中执行。 */
    @EventListener
    public void onImport(ChapterImported event) {
        admission.admit(StartCreativeRunCommand.manual(event.userId(), "chapter-assets-" + event.chapterId(),
                event.projectId(), CreativeOperationType.EXTRACT_CHAPTER_ASSETS,
                new CreativeResourceReference(CreativeResourceType.CHAPTER, event.chapterId()),
                AgentPermissionPolicy.AUTOMATION, "识别当前章节的角色、场景与道具，登记到项目并关联本章。"));
    }
}
