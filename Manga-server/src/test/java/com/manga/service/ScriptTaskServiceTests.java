package com.manga.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.config.properties.ScriptTaskProperties;
import com.manga.dto.TaskResponse;
import com.manga.entity.MangaTask;
import com.manga.repository.MangaTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证剧本导入任务同时支持 JSON 与文件上传输入。 */
class ScriptTaskServiceTests {

    /** 验证 Markdown 文件可以走 multipart 导入流程。 */
    @Test
    void shouldSubmitImportFromMultipartFile(@TempDir Path tempDir) {
        MangaTaskRepository taskRepository = mock(MangaTaskRepository.class);
        ProjectScriptService scriptService = mock(ProjectScriptService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        Executor executor = runnable -> { };
        ScriptTaskService service = new ScriptTaskService(
                taskRepository,
                scriptService,
                new ScriptTaskProperties(tempDir.toString(), 20_000_000L, 3),
                new ObjectMapper(),
                redisTemplate,
                executor
        );

        when(taskRepository.create(any())).thenAnswer(invocation -> {
            MangaTask task = invocation.getArgument(0);
            task.setId(42L);
            return task;
        });

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "novel.md",
                "text/markdown",
                "# 第一章\n正文内容".getBytes(StandardCharsets.UTF_8)
        );

        TaskResponse response = service.submitImport(7L, file, null, null, 100L, "tester");

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.projectId()).isEqualTo(7L);
        assertThat(response.status().name()).isEqualTo("QUEUED");
        assertThat(response.totalUnits()).isZero();
        assertThat(response.createdAt()).isNull();
        assertThat(response.startedAt()).isNull();
    }

    /** 验证不支持的脚本扩展名会被拒绝。 */
    @Test
    void shouldRejectUnsupportedScriptExtension(@TempDir Path tempDir) {
        ScriptTaskService service = new ScriptTaskService(
                mock(MangaTaskRepository.class),
                mock(ProjectScriptService.class),
                new ScriptTaskProperties(tempDir.toString(), 20_000_000L, 3),
                new ObjectMapper(),
                mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS),
                runnable -> { }
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "novel.pdf",
                "application/pdf",
                "content".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.submitImport(7L, file, null, null, 100L, "tester"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getResponseCode()).isEqualTo(CommonResponseCode.VALIDATION_ERROR));
    }
}
