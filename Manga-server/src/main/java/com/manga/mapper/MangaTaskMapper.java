package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.MangaTask;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 定义统一后台任务的数据访问契约。 */
public interface MangaTaskMapper extends BaseMapper<MangaTask> {
    List<MangaTask> findByOwner(@Param("ownerUserId") long ownerUserId);
    List<MangaTask> findActive(@Param("ownerUserId") long ownerUserId);
    List<MangaTask> findRecoverable();
    int updateProgress(@Param("task") MangaTask task);
}
