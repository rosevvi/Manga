package com.manga.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.domain.CreativeStepStatus;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeRun;
import com.manga.entity.CreativeStep;
import com.manga.entity.ProjectScript;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import com.manga.repository.ProjectScriptRepository;
import com.manga.service.ProjectService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证解析报告只读取当前账号的任务，并保留上一次完成结果。 */
class ProjectScriptAnalysisQueryServiceTests {
    private static final long PROJECT_ID = 12L;
    private static final long USER_ID = 34L;
    private static final long SCRIPT_ID = 56L;

    private final ProjectService projects = mock(ProjectService.class);
    private final ProjectScriptRepository scripts = mock(ProjectScriptRepository.class);
    private final CreativeRunRepository runs = mock(CreativeRunRepository.class);
    private final CreativeStepRepository steps = mock(CreativeStepRepository.class);
    private final ProjectScriptAnalysisQueryService service =
            new ProjectScriptAnalysisQueryService(projects, scripts, runs, steps, new ObjectMapper());

    @Test
    void shouldRetainCompletedReportWhileNewAnalysisIsRunning() {
        var finishedAt = LocalDateTime.of(2026, 11, 10, 10, 0);
        var running = CreativeRun.builder().runId("new-analysis").status(CreativeRunStatus.ACTIVE).build();
        var completed = CreativeRun.builder().runId("previous-analysis")
                .status(CreativeRunStatus.COMPLETED).finishedAt(finishedAt).build();
        var root = CreativeStep.builder().status(CreativeStepStatus.COMPLETED)
                .outputJson("{\"content\":\"## 故事主线\"}").build();
        when(scripts.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(ProjectScript.builder().id(SCRIPT_ID).build()));
        when(runs.findLatestManualForTarget(USER_ID, PROJECT_ID, SCRIPT_ID, CreativeOperationType.PARSE_SCRIPT, null))
                .thenReturn(Optional.of(running));
        when(runs.findLatestManualForTarget(USER_ID, PROJECT_ID, SCRIPT_ID,
                CreativeOperationType.PARSE_SCRIPT, CreativeRunStatus.COMPLETED)).thenReturn(Optional.of(completed));
        when(steps.findByRunId(completed.getRunId())).thenReturn(List.of(root));

        var result = service.find(PROJECT_ID, USER_ID);
        assertThat(result.latestRun().runId()).isEqualTo(running.getRunId());
        assertThat(result.latestRun().status()).isEqualTo(CreativeRunStatus.ACTIVE);
        assertThat(result.report().runId()).isEqualTo(completed.getRunId());
        assertThat(result.report().finishedAt()).isEqualTo(finishedAt);
        assertThat(result.report().markdown()).isEqualTo("## 故事主线");
    }

    @Test
    void shouldRejectInaccessibleProjectBeforeReadingResults() {
        when(projects.requireAccessibleProject(PROJECT_ID, USER_ID))
                .thenThrow(new BusinessException(CommonResponseCode.FORBIDDEN));

        assertThatThrownBy(() -> service.find(PROJECT_ID, USER_ID)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(scripts, runs, steps);
    }
}
