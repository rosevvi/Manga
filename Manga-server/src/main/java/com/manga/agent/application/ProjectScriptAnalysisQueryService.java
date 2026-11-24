package com.manga.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.api.ProjectScriptAnalysis;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.agent.execution.AgentStepResult;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import com.manga.repository.ProjectScriptRepository;
import com.manga.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

/** 从持久化解析 Run 恢复本用户对当前剧本的进度与报告。 */
@Service
@RequiredArgsConstructor
public class ProjectScriptAnalysisQueryService {
    private final ProjectService projects;
    private final ProjectScriptRepository scripts;
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final ObjectMapper objectMapper;

    /** 两个查询使用同一数据库快照，重新解析期间可取回前一版报告。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectScriptAnalysis find(long projectId, long userId) {
        projects.requireAccessibleProject(projectId, userId);
        return scripts.findByProjectId(projectId)
                .map(script -> findForScript(projectId, userId, script.getId()))
                .orElseGet(() -> new ProjectScriptAnalysis(null, null));
    }

    private ProjectScriptAnalysis findForScript(long projectId, long userId, long scriptId) {
        var latest = runs.findLatestManualForTarget(userId, projectId, scriptId, CreativeOperationType.PARSE_SCRIPT, null);
        if (latest.isEmpty()) {
            return new ProjectScriptAnalysis(null, null);
        }
        CreativeRun attempt = latest.get();
        var completed = latest.filter(run -> run.getStatus() == CreativeRunStatus.COMPLETED)
                .or(() -> runs.findLatestManualForTarget(userId, projectId, scriptId,
                        CreativeOperationType.PARSE_SCRIPT, CreativeRunStatus.COMPLETED));
        return new ProjectScriptAnalysis(
                new ProjectScriptAnalysis.Attempt(attempt.getRunId(), attempt.getStatus(), attempt.getErrorMessage()),
                completed.map(this::readReport).orElse(null));
    }

    /** 根 Agent 最终正文由完成事务保存到 output_json；result_json 是 Worker 提交的结构化结果。 */
    private ProjectScriptAnalysis.Report readReport(CreativeRun run) {
        return steps.findByRunId(run.getRunId()).stream()
                .filter(step -> Objects.isNull(step.getParentStepId()) && step.getStatus() == CreativeStepStatus.COMPLETED)
                .map(CreativeStep::getOutputJson)
                .filter(StringUtils::hasText)
                .map(this::readContent)
                .filter(StringUtils::hasText)
                .findFirst()
                .map(markdown -> new ProjectScriptAnalysis.Report(run.getRunId(), run.getFinishedAt(), markdown))
                .orElse(null);
    }

    private String readContent(String resultJson) {
        try {
            return objectMapper.readValue(resultJson, AgentStepResult.class).content();
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(CreativeRuntimeMessages.SCRIPT_ANALYSIS_RESULT_INVALID, failure);
        }
    }
}
