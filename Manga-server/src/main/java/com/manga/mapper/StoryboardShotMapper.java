package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.StoryboardShot;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 定义项目分镜镜头及其顺序的数据访问契约。 */
public interface StoryboardShotMapper extends BaseMapper<StoryboardShot> {

    /** 查询项目分镜镜头列表。 */
    List<StoryboardShot> findAllByProjectId(@Param("projectId") long projectId, @Param("chapterId") Long chapterId);

    /** 查询项目内指定分镜镜头。 */
    StoryboardShot findByProjectAndId(@Param("projectId") long projectId, @Param("shotId") long shotId);

    /** 查询项目下一个分镜顺序值。 */
    int findNextSortOrder(@Param("projectId") long projectId);

    /** 更新项目内的分镜镜头。 */
    int updateInProject(@Param("shot") StoryboardShot shot, @Param("projectId") long projectId);

    /** 批量更新分镜镜头顺序。 */
    int updateOrder(
            @Param("projectId") long projectId,
            @Param("shotIds") List<Long> shotIds,
            @Param("actor") String actor);
}
