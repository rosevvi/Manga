package com.manga.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.enums.ProjectWorkflowStage;
import com.manga.common.exception.BusinessException;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.ProjectScriptResponse;
import com.manga.dto.ProjectScriptSaveRequest;
import com.manga.entity.*;
import com.manga.repository.ProjectScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

import static com.manga.common.enums.ProjectResponseCode.SCRIPT_INVALID;

/** 管理项目剧本原文、分集、场景和对白。 */
@Service
@RequiredArgsConstructor
public class ProjectScriptService {

    private final ProjectService projectService;
    private final ProjectScriptRepository scriptRepository;
    private final ProjectWorkflowService workflowService;
    private final ObjectMapper objectMapper;

    /** 查询项目剧本，不存在时返回空。 */
    public ProjectScriptResponse find(long projectId) {
        requireAccessibleProject(projectId);
        return scriptRepository.findByProjectId(projectId).map(this::toResponse).orElse(null);
    }

    /** 保存人工编辑或导入后的结构化剧本。 */
    @Transactional
    public ProjectScriptResponse save(long projectId, ProjectScriptSaveRequest request) {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        String actor = SecurityUtils.requireCurrentUsername();
        return saveForOwner(projectId, request, ownerUserId, actor);
    }

    /** 为已完成权限校验的异步生成任务保存剧本。 */
    @Transactional
    ProjectScriptResponse saveForOwner(long projectId, ProjectScriptSaveRequest request,
            long ownerUserId, String actor) {
        MangaProject project = projectService.requireOwnedProject(projectId, ownerUserId);
        String rawContent = request.rawContent().trim();
        List<ProjectScriptSaveRequest.Episode> episodes = request.episodes() == null
                ? List.of() : request.episodes();
        String structureJson = serializeStructure(request, episodes);
        ProjectScript current = scriptRepository.findByProjectId(projectId).orElse(null);
        ProjectScript script = ProjectScript.builder()
                .id(current == null ? null : current.getId())
                .projectId(projectId)
                .title(normalize(request.title(), project.getName()))
                .synopsis(normalize(request.synopsis(), null))
                .rawContent(rawContent)
                .sourceType(normalize(request.sourceType(), "MANUAL"))
                .parseStatus(episodes.isEmpty() ? "DRAFT" : "COMPLETED")
                .structureVersion(current == null || current.getStructureVersion() == null
                        ? 1 : current.getStructureVersion() + 1)
                .structureJson(structureJson)
                .lastError(null)
                .createdBy(current == null ? actor : current.getCreatedBy())
                .updatedBy(actor)
                .build();
        if (current == null) {
            scriptRepository.create(script);
        } else {
            scriptRepository.update(script);
        }
        replaceStructure(script.getId(), episodes);
        if (!episodes.isEmpty()) {
            workflowService.advanceTo(projectId, ProjectWorkflowStage.STORYBOARD, actor);
        } else {
            workflowService.setStage(projectId, ProjectWorkflowStage.SCRIPT, actor);
        }
        return toResponse(scriptRepository.findByProjectId(projectId).orElse(script));
    }

    /** 导入纯文本剧本并保留为待结构化内容。 */
    @Transactional
    public ProjectScriptResponse importText(long projectId, String rawContent, String sourceType) {
        MangaProject project = requireOwnedProject(projectId);
        ProjectScript current = scriptRepository.findByProjectId(projectId).orElse(null);
        ProjectScriptSaveRequest request = new ProjectScriptSaveRequest(
                current == null ? project.getName() : current.getTitle(),
                current == null ? null : current.getSynopsis(),
                rawContent,
                sourceType,
                current == null ? List.of() : toResponse(current).episodes());
        return save(projectId, request);
    }

    /** 解析持久化后的结构化剧本响应。 */
    private ProjectScriptResponse toResponse(ProjectScript script) {
        List<ProjectScriptEpisode> episodeEntities = scriptRepository.findEpisodes(script.getId());
        List<ProjectScriptScene> sceneEntities = scriptRepository.findScenes(script.getId());
        List<ProjectScriptDialogue> dialogueEntities = scriptRepository.findDialogues(script.getId());
        Map<Long, List<ProjectScriptDialogue>> dialoguesByScene = new HashMap<>();
        dialogueEntities.forEach(dialogue -> dialoguesByScene
                .computeIfAbsent(dialogue.getSceneId(), ignored -> new ArrayList<>()).add(dialogue));
        Map<Long, List<ProjectScriptScene>> scenesByEpisode = new HashMap<>();
        sceneEntities.forEach(scene -> scenesByEpisode
                .computeIfAbsent(scene.getEpisodeId(), ignored -> new ArrayList<>()).add(scene));
        List<ProjectScriptSaveRequest.Episode> episodes = episodeEntities.stream()
                .map(episode -> new ProjectScriptSaveRequest.Episode(
                        episode.getEpisodeNumber(), episode.getTitle(), episode.getSummary(),
                        scenesByEpisode.getOrDefault(episode.getId(), Collections.emptyList()).stream()
                                .map(scene -> new ProjectScriptSaveRequest.Scene(
                                        scene.getSceneNumber(), scene.getLocation(), scene.getTimeDescription(),
                                        scene.getSummary(), scene.getContent(),
                                        dialoguesByScene.getOrDefault(scene.getId(), Collections.emptyList()).stream()
                                                .map(dialogue -> new ProjectScriptSaveRequest.Dialogue(
                                                        dialogue.getSpeaker(), dialogue.getText(), dialogue.getDialogueType()))
                                                .toList()))
                                .toList()))
                .toList();
        return new ProjectScriptResponse(
                script.getId(), script.getProjectId(), script.getTitle(), script.getSynopsis(), script.getRawContent(),
                script.getSourceType(), script.getParseStatus(), script.getStructureVersion(), episodes,
                script.getLastError(), script.getCreatedAt(), script.getUpdatedAt());
    }

    /** 替换剧本结构化明细。 */
    private void replaceStructure(long scriptId, List<ProjectScriptSaveRequest.Episode> episodes) {
        scriptRepository.deleteDialogues(scriptId);
        scriptRepository.deleteScenes(scriptId);
        scriptRepository.deleteEpisodes(scriptId);
        for (int episodeIndex = 0; episodeIndex < episodes.size(); episodeIndex++) {
            ProjectScriptSaveRequest.Episode episode = episodes.get(episodeIndex);
            ProjectScriptEpisode episodeEntity = ProjectScriptEpisode.builder()
                    .scriptId(scriptId)
                    .episodeNumber(episode.episodeNumber() == null ? episodeIndex + 1 : episode.episodeNumber())
                    .title(normalize(episode.title(), "第 " + (episodeIndex + 1) + " 集"))
                    .summary(normalize(episode.summary(), null))
                    .sortOrder(episodeIndex + 1)
                    .build();
            scriptRepository.createEpisode(episodeEntity);
            if (episode.scenes() == null) {
                continue;
            }
            for (int sceneIndex = 0; sceneIndex < episode.scenes().size(); sceneIndex++) {
                ProjectScriptSaveRequest.Scene scene = episode.scenes().get(sceneIndex);
                ProjectScriptScene sceneEntity = ProjectScriptScene.builder()
                        .episodeId(episodeEntity.getId())
                        .sceneNumber(scene.sceneNumber() == null ? sceneIndex + 1 : scene.sceneNumber())
                        .location(normalize(scene.location(), null))
                        .timeDescription(normalize(scene.time(), null))
                        .summary(normalize(scene.summary(), null))
                        .content(normalize(scene.content(), null))
                        .sortOrder(sceneIndex + 1)
                        .build();
                scriptRepository.createScene(sceneEntity);
                if (scene.dialogues() == null) {
                    continue;
                }
                for (int dialogueIndex = 0; dialogueIndex < scene.dialogues().size(); dialogueIndex++) {
                    ProjectScriptSaveRequest.Dialogue dialogue = scene.dialogues().get(dialogueIndex);
                    scriptRepository.createDialogue(ProjectScriptDialogue.builder()
                            .sceneId(sceneEntity.getId())
                            .speaker(normalize(dialogue.speaker(), null))
                            .text(normalize(dialogue.text(), ""))
                            .dialogueType(normalize(dialogue.type(), "DIALOGUE"))
                            .sortOrder(dialogueIndex + 1)
                            .build());
                }
            }
        }
    }

    private MangaProject requireOwnedProject(long projectId) {
        return projectService.requireOwnedProject(projectId,
                com.manga.common.security.SecurityUtils.requireCurrentUserId());
    }

    private MangaProject requireAccessibleProject(long projectId) {
        return projectService.requireAccessibleProject(projectId,
                com.manga.common.security.SecurityUtils.requireCurrentUserId());
    }

    private String serializeStructure(ProjectScriptSaveRequest request,
            List<ProjectScriptSaveRequest.Episode> episodes) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "title", request.title(),
                    "synopsis", request.synopsis() == null ? "" : request.synopsis(),
                    "episodes", episodes));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(SCRIPT_INVALID, "剧本结构序列化失败", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
