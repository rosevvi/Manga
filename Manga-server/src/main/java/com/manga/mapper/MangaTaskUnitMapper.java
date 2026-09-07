package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.MangaTaskUnit;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 定义后台任务处理单元的数据访问契约。 */
public interface MangaTaskUnitMapper extends BaseMapper<MangaTaskUnit> {
    List<MangaTaskUnit> findByTaskId(@Param("taskId") long taskId);
}
