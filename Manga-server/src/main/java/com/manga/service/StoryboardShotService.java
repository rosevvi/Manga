package com.manga.service;

import com.manga.agent.changeset.StoryboardChangeSet;
import com.manga.common.enums.ProjectResponseCode;
import com.manga.common.enums.ProjectWorkflowStage;
import com.manga.common.enums.StoryboardShotStatus;
import com.manga.common.exception.BusinessException;
import com.manga.common.security.SecurityUtils;
import com.manga.dto.StoryboardShotCreateRequest;
import com.manga.dto.StoryboardShotOrderRequest;
import com.manga.dto.StoryboardShotResponse;
import com.manga.dto.StoryboardShotUpdateRequest;
import com.manga.entity.StoryboardShot;
import com.manga.repository.StoryboardShotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static com.manga.common.constant.StoryboardConstants.SHOT_NUMBER_FORMAT;

/** 提供项目内分镜镜头的编辑、删除与批量排序能力。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StoryboardShotService {

    private final StoryboardShotRepository storyboardShotRepository;
    private final ProjectService projectService;
    private final ProjectWorkflowService workflowService;
    private final ProjectRevisionService revisionService;

    /** 查询项目分镜镜头列表。 */
    public List<StoryboardShotResponse> findAll(long projectId, Long chapterId) {
        return findAllForUser(projectId, SecurityUtils.requireCurrentUserId(), chapterId);
    }

    /** 按显式用户范围查询分镜，供异步助手工具调用。 */
    public List<StoryboardShotResponse> findAllForUser(long projectId, long userId, Long chapterId) {
        projectService.requireAccessibleProject(projectId, userId);
        return storyboardShotRepository.findAllByProjectId(projectId, chapterId).stream().map(this::toResponse).toList();
    }

    /** 创建项目分镜镜头。 */
    @Transactional
    public StoryboardShotResponse create(long projectId, StoryboardShotCreateRequest request) {
        requireProject(projectId);
        advanceChapterRevision(projectId, request.chapterId());
        String actor = SecurityUtils.requireCurrentUsername();
        int sortOrder = storyboardShotRepository.nextSortOrder(projectId);
        StoryboardShot shot = StoryboardShot.builder()
                .projectId(projectId)
                .chapterId(request.chapterId())
                .sortOrder(sortOrder)
                .shotNumber(SHOT_NUMBER_FORMAT.formatted(sortOrder))
                .title(request.title().trim())
                .sceneName(normalize(request.sceneName()))
                .shotType(normalize(request.shotType()))
                .cameraMovement(normalize(request.cameraMovement()))
                .durationSeconds(request.durationSeconds() == null ? 0 : request.durationSeconds())
                .content(normalize(request.content()))
                .dialogue(normalize(request.dialogue()))
                .soundEffect(normalize(request.soundEffect()))
                .imageUrl(normalize(request.imageUrl()))
                .notes(normalize(request.notes()))
                .status(request.status() == null ? StoryboardShotStatus.DRAFT : request.status())
                .createdBy(actor)
                .updatedBy(actor)
                .build();
        storyboardShotRepository.create(shot);
        workflowService.advanceTo(projectId, ProjectWorkflowStage.ASSETS, actor);
        log.info("Storyboard shot created projectId={} shotId={} sortOrder={}",
                projectId, shot.getId(), sortOrder);
        return toResponse(requireShot(projectId, shot.getId()));
    }

    /** 更新项目分镜镜头。 */
    @Transactional
    public StoryboardShotResponse update(
            long projectId, long shotId, StoryboardShotUpdateRequest request) {
        requireProject(projectId);
        StoryboardShot current = requireShot(projectId, shotId);
        advanceChapterRevision(projectId, current.getChapterId());
        if (!Objects.equals(current.getChapterId(), request.chapterId())) advanceChapterRevision(projectId, request.chapterId());
        StoryboardShot shot = StoryboardShot.builder()
                .id(shotId)
                .chapterId(request.chapterId())
                .title(request.title().trim())
                .sceneName(normalize(request.sceneName()))
                .shotType(normalize(request.shotType()))
                .cameraMovement(normalize(request.cameraMovement()))
                .durationSeconds(request.durationSeconds() == null ? 0 : request.durationSeconds())
                .content(normalize(request.content()))
                .dialogue(normalize(request.dialogue()))
                .soundEffect(normalize(request.soundEffect()))
                .imageUrl(normalize(request.imageUrl()))
                .notes(normalize(request.notes()))
                .status(request.status() == null ? current.getStatus() : request.status())
                .updatedBy(SecurityUtils.requireCurrentUsername())
                .build();
        storyboardShotRepository.update(shot, projectId);
        workflowService.advanceTo(projectId, ProjectWorkflowStage.ASSETS,
                SecurityUtils.requireCurrentUsername());
        log.info("Storyboard shot updated projectId={} shotId={} status={}",
                projectId, shotId, shot.getStatus());
        return toResponse(requireShot(projectId, shotId));
    }

    /** 删除项目分镜镜头并整理顺序。 */
    @Transactional
    public void delete(long projectId, long shotId) {
        requireProject(projectId);
        StoryboardShot current = requireShot(projectId, shotId);
        advanceChapterRevision(projectId, current.getChapterId());
        storyboardShotRepository.delete(shotId);
        List<Long> remainingIds = storyboardShotRepository.findAllByProjectId(projectId, null).stream()
                .map(StoryboardShot::getId)
                .toList();
        if (!remainingIds.isEmpty()) {
            storyboardShotRepository.updateOrder(projectId, remainingIds, SecurityUtils.requireCurrentUsername());
        }
        log.info("Storyboard shot deleted projectId={} shotId={}", projectId, shotId);
    }

    /** 校验并批量调整分镜镜头顺序。 */
    @Transactional
    public List<StoryboardShotResponse> reorder(long projectId, StoryboardShotOrderRequest request) {
        requireProject(projectId);
        List<StoryboardShot> currentShots = storyboardShotRepository.findAllByProjectId(projectId, null);
        List<Long> currentIds = currentShots.stream().map(StoryboardShot::getId).toList();
        List<Long> requestedIds = request.shotIds();
        boolean containsDuplicates = new HashSet<>(requestedIds).size() != requestedIds.size();
        if (containsDuplicates || currentIds.size() != requestedIds.size()
                || !new HashSet<>(currentIds).equals(new HashSet<>(requestedIds))) {
            throw new BusinessException(ProjectResponseCode.STORYBOARD_ORDER_INVALID);
        }
        storyboardShotRepository.updateOrder(projectId, requestedIds, SecurityUtils.requireCurrentUsername());
        currentShots.stream().map(StoryboardShot::getChapterId).filter(Objects::nonNull).distinct()
                .forEach(chapterId -> advanceChapterRevision(projectId, chapterId));
        workflowService.advanceTo(projectId, ProjectWorkflowStage.ASSETS,
                SecurityUtils.requireCurrentUsername());
        log.info("Storyboard order updated projectId={} shotCount={}", projectId, requestedIds.size());
        return storyboardShotRepository.findAllByProjectId(projectId, null).stream().map(this::toResponse).toList();
    }

    /** 校验当前用户可访问目标项目。 */
    private void requireProject(long projectId) {
        revisionService.edit(projectId, SecurityUtils.requireCurrentUserId(), null, SecurityUtils.requireCurrentUsername());
    }

    /** 手动镜头写入也推进目标章节版本，并校验章节属于项目。 */
    private void advanceChapterRevision(long projectId, Long chapterId) {
        if (Objects.nonNull(chapterId)) revisionService.edit(projectId, SecurityUtils.requireCurrentUserId(),
                chapterId, SecurityUtils.requireCurrentUsername());
    }

    /** 在审批事务中批量追加草稿；整批任一写入失败时全部回滚。 */
    @Transactional
    public List<Long> applyApproved(long projectId, long userId, String actor, StoryboardChangeSet proposal) {
        revisionService.edit(projectId, userId, proposal.chapterId(), actor);
        int firstOrder = storyboardShotRepository.nextSortOrder(projectId);
        List<Long> ids = new ArrayList<>();
        for (StoryboardChangeSet.Shot proposed : proposal.shots()) {
            int order = firstOrder + ids.size();
            StoryboardShot shot = StoryboardShot.builder().projectId(projectId).chapterId(proposal.chapterId())
                    .sortOrder(order).shotNumber(SHOT_NUMBER_FORMAT.formatted(order)).title(proposed.title().trim())
                    .sceneName(normalize(proposed.sceneName())).shotType(normalize(proposed.shotType()))
                    .cameraMovement(normalize(proposed.cameraMovement())).durationSeconds(Objects.requireNonNullElse(proposed.durationSeconds(), 0))
                    .content(normalize(proposed.content())).dialogue(normalize(proposed.dialogue()))
                    .soundEffect(normalize(proposed.soundEffect())).notes(normalize(proposed.notes()))
                    .status(StoryboardShotStatus.DRAFT).createdBy(actor).updatedBy(actor).build();
            storyboardShotRepository.create(shot);
            ids.add(shot.getId());
        }
        workflowService.advanceTo(projectId, ProjectWorkflowStage.ASSETS, actor);
        return List.copyOf(ids);
    }

    /** 查询并校验项目内的分镜镜头。 */
    private StoryboardShot requireShot(long projectId, long shotId) {
        return storyboardShotRepository.findByProjectAndId(projectId, shotId)
                .orElseThrow(() -> new BusinessException(
                        ProjectResponseCode.STORYBOARD_SHOT_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /** 将分镜镜头转换为响应对象。 */
    private StoryboardShotResponse toResponse(StoryboardShot shot) {
        return new StoryboardShotResponse(
                shot.getId(), shot.getProjectId(), shot.getChapterId(), shot.getSortOrder(), shot.getShotNumber(), shot.getTitle(),
                shot.getSceneName(), shot.getShotType(), shot.getCameraMovement(), shot.getDurationSeconds(),
                shot.getContent(), shot.getDialogue(), shot.getSoundEffect(), shot.getImageUrl(), shot.getNotes(),
                shot.getStatus(), shot.getCreatedAt(), shot.getUpdatedAt());
    }

    /** 去除字符串首尾空白并处理空值。 */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
