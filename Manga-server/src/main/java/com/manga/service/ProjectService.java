package com.manga.service;

import com.manga.common.constant.ProjectConstants;
import com.manga.common.enums.ProjectResponseCode;
import com.manga.common.enums.ProjectMemberRole;
import com.manga.common.enums.ProjectStatus;
import com.manga.common.enums.ProjectVisibilityScope;
import com.manga.common.enums.ProjectWorkflowStage;
import com.manga.common.exception.BusinessException;
import com.manga.common.security.SecurityUtils;
import com.manga.config.ArtStylePresets;
import com.manga.dto.ArtStylePresetResponse;
import com.manga.dto.ProjectMemberResponse;
import com.manga.dto.ProjectCreateRequest;
import com.manga.dto.ProjectResponse;
import com.manga.dto.ProjectUpdateRequest;
import com.manga.entity.MangaProject;
import com.manga.entity.ProjectMember;
import com.manga.repository.ProjectMemberRepository;
import com.manga.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 提供当前用户项目的查询和生命周期管理。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectWorkflowService workflowService;

    /** 查询当前用户可访问的项目列表。 */
    public List<ProjectResponse> findAll() {
        long userId = SecurityUtils.requireCurrentUserId();
        return projectRepository.findAllAccessibleBy(userId).stream().map(this::toResponse).toList();
    }

    /** 查询当前用户可访问的项目详情。 */
    public ProjectResponse findById(long projectId) {
        long userId = SecurityUtils.requireCurrentUserId();
        MangaProject project = requireAccessibleProject(projectId, userId);
        project.setWorkflowStage(workflowService.getOrCreate(projectId, SecurityUtils.requireCurrentUsername()).getCurrentStage());
        return toResponse(project);
    }

    /** 查询当前用户可访问的项目成员。 */
    public List<ProjectMemberResponse> findMembers(long projectId) {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        requireOwnedProject(projectId, ownerUserId);
        return projectMemberRepository.findMembersByProject(projectId).stream().map(this::toMemberResponse).toList();
    }

    /** 返回项目可选画风预设。 */
    public List<ArtStylePresetResponse> findArtStylePresets() {
        return ArtStylePresets.getAll();
    }

    /** 创建项目并建立所有者成员关系。 */
    @Transactional
    public ProjectResponse create(ProjectCreateRequest request) {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        String actor = SecurityUtils.requireCurrentUsername();
        MangaProject project = MangaProject.builder()
                .ownerUserId(ownerUserId)
                .name(request.name().trim())
                .description(normalize(request.description()))
                .coverUrl(normalize(request.coverUrl()))
                .genre(normalize(request.genre()))
                .aspectRatio(resolveAspectRatio(request.aspectRatio()))
                .visibilityScope(request.visibilityScope() == null
                        ? ProjectVisibilityScope.PRIVATE
                        : request.visibilityScope())
                .status(request.status() == null ? ProjectStatus.DRAFT : request.status())
                .artStyle(resolveArtStyle(request.artStyle()))
                .artStyleDescription(normalize(request.artStyleDescription()))
                .artStyleImagePrompt(normalize(request.artStyleImagePrompt()))
                .artStyleImageUrl(normalize(request.artStyleImageUrl()))
                .createdBy(actor)
                .updatedBy(actor)
                .build();
        projectRepository.create(project);
        projectMemberRepository.create(ProjectMember.builder()
                .projectId(project.getId())
                .userId(ownerUserId)
                .role(ProjectMemberRole.OWNER)
                .createdBy(actor)
                .updatedBy(actor)
                .build());
        project.setWorkflowStage(workflowService.getOrCreate(project.getId(), actor).getCurrentStage());
        log.info("Project created projectId={} ownerUserId={}", project.getId(), ownerUserId);
        return toResponse(requireOwnedProject(project.getId(), ownerUserId));
    }

    /** 更新当前用户拥有的项目。 */
    @Transactional
    public ProjectResponse update(long projectId, ProjectUpdateRequest request) {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        requireOwnedProject(projectId, ownerUserId);
        MangaProject project = MangaProject.builder()
                .id(projectId)
                .name(request.name().trim())
                .description(normalize(request.description()))
                .coverUrl(normalize(request.coverUrl()))
                .genre(normalize(request.genre()))
                .aspectRatio(resolveAspectRatio(request.aspectRatio()))
                .visibilityScope(request.visibilityScope() == null
                        ? ProjectVisibilityScope.PRIVATE
                        : request.visibilityScope())
                .status(request.status() == null ? ProjectStatus.DRAFT : request.status())
                .artStyle(resolveArtStyle(request.artStyle()))
                .artStyleDescription(normalize(request.artStyleDescription()))
                .artStyleImagePrompt(normalize(request.artStyleImagePrompt()))
                .artStyleImageUrl(normalize(request.artStyleImageUrl()))
                .updatedBy(SecurityUtils.requireCurrentUsername())
                .build();
        projectRepository.update(project, ownerUserId);
        log.info("Project updated projectId={} ownerUserId={} status={}",
                projectId, ownerUserId, project.getStatus());
        return toResponse(requireOwnedProject(projectId, ownerUserId));
    }

    /** 删除当前用户拥有的项目。 */
    @Transactional
    public void delete(long projectId) {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        requireOwnedProject(projectId, ownerUserId);
        projectRepository.delete(projectId);
        log.info("Project deleted projectId={} ownerUserId={}", projectId, ownerUserId);
    }

    /** 查询并校验当前用户拥有的项目。 */
    public MangaProject requireOwnedProject(long projectId, long ownerUserId) {
        return projectRepository.findOwnedById(projectId, ownerUserId)
                .orElseThrow(() -> new BusinessException(ProjectResponseCode.PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /** 查询并校验当前用户可访问的项目。 */
    public MangaProject requireAccessibleProject(long projectId, long userId) {
        return projectRepository.findAccessibleById(projectId, userId)
                .orElseThrow(() -> new BusinessException(ProjectResponseCode.PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /** 将项目转换为详情响应。 */
    private ProjectResponse toResponse(MangaProject project) {
        ArtStylePresetResponse preset = ArtStylePresets.getByKey(project.getArtStyle());
        return new ProjectResponse(
                project.getId(), project.getName(), project.getDescription(), project.getCoverUrl(), project.getGenre(),
                project.getAspectRatio(), project.getVisibilityScope(), project.getStatus(),
                project.getWorkflowStage() == null ? ProjectWorkflowStage.SCRIPT : project.getWorkflowStage(),
                project.getArtStyle(), preset == null ? null : preset.name(), project.getArtStyleDescription(),
                project.getArtStyleImagePrompt(), project.getArtStyleImageUrl(),
                project.getShotCount() == null ? 0 : project.getShotCount(),
                project.getCreatedAt(), project.getUpdatedAt());
    }

    /** 将项目成员转换为响应对象。 */
    private ProjectMemberResponse toMemberResponse(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getId(), member.getProjectId(), member.getUserId(), member.getUsername(), member.getDisplayName(),
                member.getRole(), member.getCreatedAt(), member.getUpdatedAt());
    }

    /** 解析项目画面比例并提供默认值。 */
    private String resolveAspectRatio(String value) {
        String aspectRatio = normalize(value);
        if (aspectRatio == null) {
            return ProjectConstants.DEFAULT_ASPECT_RATIO;
        }
        if (!ProjectConstants.SUPPORTED_ASPECT_RATIOS.contains(aspectRatio)) {
            throw new BusinessException(
                    ProjectResponseCode.PROJECT_ASPECT_RATIO_INVALID,
                    ProjectConstants.PROJECT_ASPECT_RATIO_INVALID,
                    HttpStatus.BAD_REQUEST);
        }
        return aspectRatio;
    }

    /** 解析项目画风并提供默认值。 */
    private String resolveArtStyle(String value) {
        String artStyle = normalize(value);
        if (artStyle == null || ProjectConstants.CUSTOM_ART_STYLE_KEY.equals(artStyle)) {
            return artStyle;
        }
        if (ArtStylePresets.getByKey(artStyle) == null) {
            throw new BusinessException(
                    ProjectResponseCode.PROJECT_ART_STYLE_INVALID,
                    ProjectConstants.PROJECT_ART_STYLE_INVALID,
                    HttpStatus.BAD_REQUEST);
        }
        return artStyle;
    }

    /** 去除字符串首尾空白并处理空值。 */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
