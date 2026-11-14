package com.manga.agent.application;
import com.manga.agent.api.CreativeManualRunPage;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeRunRepository;
import com.manga.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
/** 按认证用户和项目找回手动创作任务，不依赖浏览器缓存。 */
@Service
@RequiredArgsConstructor
public class CreativeManualRunQueryService {
    /** 单页上限，额外读取一条判断是否还有更早任务。 */
    private static final int PAGE_SIZE = 25;
    private final ProjectService projects;
    private final CreativeRunRepository runs;
    /** 验证所有者后返回历史页与活动任务；分页不会遗漏更早的未决审批。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CreativeManualRunPage list(long userId, Long projectId, Long beforeId) {
        if (Objects.nonNull(projectId)) projects.requireOwnedProject(projectId, userId);
        List<CreativeRun> rows = runs.findManual(userId, projectId, beforeId, PAGE_SIZE + 1);
        List<CreativeRun> page = rows.stream().limit(PAGE_SIZE).toList();
        return new CreativeManualRunPage(page.stream().map(this::item).toList(),
                rows.size() > PAGE_SIZE ? page.getLast().getId() : null,
                runs.findActiveManual(userId, projectId).map(this::item).orElse(null));
    }
    /** 只公开选择任务所需的稳定字段。 */
    private CreativeManualRunPage.Item item(CreativeRun run) {
        return new CreativeManualRunPage.Item(run.getId(), run.getRunId(), run.getOperationType(),
                run.getTargetId(), run.getStatus(), run.getCreatedAt(), run.getProjectId());
    }
}
