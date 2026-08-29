package com.manga.repository;

import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.entity.StoryboardShot;
import com.manga.mapper.StoryboardShotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 使用 MyBatis-Plus 持久化项目中的分镜镜头。 */
@Repository
@RequiredArgsConstructor
public class StoryboardShotRepository {

    private final StoryboardShotMapper storyboardShotMapper;

    public List<StoryboardShot> findAllByProjectId(long projectId) {
        return storyboardShotMapper.findAllByProjectId(projectId);
    }

    public Optional<StoryboardShot> findByProjectAndId(long projectId, long shotId) {
        return Optional.ofNullable(storyboardShotMapper.findByProjectAndId(projectId, shotId));
    }

    public int nextSortOrder(long projectId) {
        return storyboardShotMapper.findNextSortOrder(projectId);
    }

    public StoryboardShot create(StoryboardShot shot) {
        storyboardShotMapper.insert(shot);
        if (shot.getId() == null) {
            throw new IllegalStateException(ExceptionMessageConstants.GENERATED_STORYBOARD_SHOT_ID_UNAVAILABLE);
        }
        return shot;
    }

    public void update(StoryboardShot shot, long projectId) {
        storyboardShotMapper.updateInProject(shot, projectId);
    }

    public void delete(long shotId) {
        storyboardShotMapper.deleteById(shotId);
    }

    public void updateOrder(long projectId, List<Long> shotIds, String actor) {
        storyboardShotMapper.updateOrder(projectId, shotIds, actor);
    }
}
