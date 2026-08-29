package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.StoryboardShot;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 定义项目分镜镜头及其顺序的数据访问契约。 */
public interface StoryboardShotMapper extends BaseMapper<StoryboardShot> {

    List<StoryboardShot> findAllByProjectId(@Param("projectId") long projectId);

    StoryboardShot findByProjectAndId(@Param("projectId") long projectId, @Param("shotId") long shotId);

    int findNextSortOrder(@Param("projectId") long projectId);

    int updateInProject(@Param("shot") StoryboardShot shot, @Param("projectId") long projectId);

    int updateOrder(
            @Param("projectId") long projectId,
            @Param("shotIds") List<Long> shotIds,
            @Param("actor") String actor);
}
