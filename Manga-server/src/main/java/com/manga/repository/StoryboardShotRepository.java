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

    /** 查询项目分镜镜头列表。 */
    public List<StoryboardShot> findAllByProjectId(long projectId) {
        return storyboardShotMapper.findAllByProjectId(projectId);
    }

    /** 查询项目内指定分镜镜头。 */
    public Optional<StoryboardShot> findByProjectAndId(long projectId, long shotId) {
        return Optional.ofNullable(storyboardShotMapper.findByProjectAndId(projectId, shotId));
    }

    /** 查询项目下一个分镜顺序值。 */
    public int nextSortOrder(long projectId) {
        return storyboardShotMapper.findNextSortOrder(projectId);
    }

    /** 创建分镜镜头。 */
    public StoryboardShot create(StoryboardShot shot) {
        storyboardShotMapper.insert(shot);
        if (shot.getId() == null) {
            throw new IllegalStateException(ExceptionMessageConstants.GENERATED_STORYBOARD_SHOT_ID_UNAVAILABLE);
        }
        return shot;
    }

    /** 更新分镜镜头。 */
    public void update(StoryboardShot shot, long projectId) {
        storyboardShotMapper.updateInProject(shot, projectId);
    }

    /** 删除分镜镜头。 */
    public void delete(long shotId) {
        storyboardShotMapper.deleteById(shotId);
    }

    /** 批量更新分镜镜头顺序。 */
    public void updateOrder(long projectId, List<Long> shotIds, String actor) {
        storyboardShotMapper.updateOrder(projectId, shotIds, actor);
    }
}
