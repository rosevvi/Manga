package com.manga.service;

import com.manga.common.enums.ProjectResponseCode;
import com.manga.common.enums.ProjectStatus;
import com.manga.common.exception.BusinessException;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.ProjectCreateRequest;
import com.manga.dto.ProjectResponse;
import com.manga.dto.ProjectUpdateRequest;
import com.manga.entity.MangaProject;
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

    public List<ProjectResponse> findAll() {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        return projectRepository.findAllOwnedBy(ownerUserId).stream().map(this::toResponse).toList();
    }

    public ProjectResponse findById(long projectId) {
        return toResponse(requireOwnedProject(projectId, SecurityUtils.requireCurrentUserId()));
    }

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
                .status(request.status() == null ? ProjectStatus.DRAFT : request.status())
                .createdBy(actor)
                .updatedBy(actor)
                .build();
        projectRepository.create(project);
        log.info("Project created projectId={} ownerUserId={}", project.getId(), ownerUserId);
        return toResponse(requireOwnedProject(project.getId(), ownerUserId));
    }

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
                .status(request.status() == null ? ProjectStatus.DRAFT : request.status())
                .updatedBy(SecurityUtils.requireCurrentUsername())
                .build();
        projectRepository.update(project, ownerUserId);
        log.info("Project updated projectId={} ownerUserId={} status={}",
                projectId, ownerUserId, project.getStatus());
        return toResponse(requireOwnedProject(projectId, ownerUserId));
    }

    @Transactional
    public void delete(long projectId) {
        long ownerUserId = SecurityUtils.requireCurrentUserId();
        requireOwnedProject(projectId, ownerUserId);
        projectRepository.delete(projectId);
        log.info("Project deleted projectId={} ownerUserId={}", projectId, ownerUserId);
    }

    MangaProject requireOwnedProject(long projectId, long ownerUserId) {
        return projectRepository.findOwnedById(projectId, ownerUserId)
                .orElseThrow(() -> new BusinessException(ProjectResponseCode.PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private ProjectResponse toResponse(MangaProject project) {
        return new ProjectResponse(
                project.getId(), project.getName(), project.getDescription(), project.getCoverUrl(), project.getGenre(),
                project.getStatus(), project.getShotCount() == null ? 0 : project.getShotCount(),
                project.getCreatedAt(), project.getUpdatedAt());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
