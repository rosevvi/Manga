package com.manga.service;

import com.manga.common.security.SecurityUtils;
import com.manga.dto.StoryboardVideoArtifact;
import com.manga.entity.MangaProject;
import com.manga.entity.StoryboardShot;
import com.manga.mapper.StoryboardShotMapper;
import com.manga.repository.GenerationJobRepository;
import com.manga.repository.StoryboardShotRepository;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** 验证镜头预览和视频提示词在工作台读写时的独立语义。 */
class StoryboardShotServiceTests {
    @Test
    void videoPreviewIsBatchedAndSignedWithoutPreviewingImages() {
        long projectId = 10L;
        long chapterId = 20L;
        long userId = 30L;
        long availableShotId = 40L;
        long emptyShotId = 41L;
        String storageUrl = "/media/generated/test.mp4";
        String previewUrl = "https://media.example.test/test.mp4?preview=true";
        String videoPrompt = "镜头沿林间小径缓慢推进";
        var shots = mock(StoryboardShotRepository.class);
        var projects = mock(ProjectService.class);
        var jobs = mock(GenerationJobRepository.class);
        var storage = mock(MediaStorageService.class);
        var service = new StoryboardShotService(shots, projects, mock(ProjectWorkflowService.class),
                mock(ProjectRevisionService.class), storage, jobs);

        when(projects.requireAccessibleProject(projectId, userId)).thenReturn(MangaProject.builder().ownerUserId(userId).build());
        when(shots.findAllByProjectId(projectId, chapterId)).thenReturn(List.of(
                StoryboardShot.builder().id(availableShotId).sortOrder(1).durationSeconds(5)
                        .imageUrl("image.png").videoPrompt(videoPrompt).build(),
                StoryboardShot.builder().id(emptyShotId).sortOrder(2).durationSeconds(5).imageUrl("image.png").build()));
        when(jobs.latestStoryboardVideos(userId, projectId, chapterId))
                .thenReturn(List.of(new StoryboardVideoArtifact(availableShotId, "video-job", storageUrl)));
        when(storage.previewGeneratedMedia(storageUrl)).thenReturn(previewUrl);

        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::requireCurrentUserId).thenReturn(userId);
            var result = service.findAll(projectId, chapterId, true);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().videoPreview().url()).isEqualTo(previewUrl);
            assertThat(result.getFirst().videoPreview().jobId()).isEqualTo("video-job");
            assertThat(result.getFirst().imagePreviewUrl()).isNull();
            assertThat(result.getFirst().videoPrompt()).isEqualTo(videoPrompt);
            assertThat(result.getLast().videoPreview()).isNull();
            verify(jobs).latestStoryboardVideos(userId, projectId, chapterId);
            verify(storage).previewGeneratedMedia(storageUrl);
            verifyNoMoreInteractions(jobs, storage);
        }
    }

    @Test
    void updatePreservesOmittedPromptAndAcceptsExplicitClear() throws IOException {
        String resource = "mapper/StoryboardShotMapper.xml";
        var configuration = new Configuration();
        try (var stream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement(StoryboardShotMapper.class.getName() + ".updateInProject");
        var shot = StoryboardShot.builder().id(1L).build();
        var parameters = Map.of("shot", shot, "projectId", 2L);

        assertThat(statement.getBoundSql(parameters).getSql()).doesNotContain("video_prompt");

        shot.setVideoPrompt("");
        assertThat(statement.getBoundSql(parameters).getParameterMappings())
                .anyMatch(parameter -> parameter.getProperty().equals("shot.videoPrompt"));
    }
}
