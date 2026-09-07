package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.ProjectScriptScene;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 定义剧本场景的数据访问契约。 */
public interface ProjectScriptSceneMapper extends BaseMapper<ProjectScriptScene> {

    /** 查询剧本全部场景。 */
    List<ProjectScriptScene> findByChapterId(@Param("chapterId") long chapterId);

    /** 删除剧本全部场景。 */
    int deleteByChapterId(@Param("chapterId") long chapterId);
}
