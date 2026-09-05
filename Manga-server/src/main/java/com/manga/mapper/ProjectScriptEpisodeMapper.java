package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.ProjectScriptEpisode;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 定义剧本分集的数据访问契约。 */
public interface ProjectScriptEpisodeMapper extends BaseMapper<ProjectScriptEpisode> {

    /** 查询剧本分集。 */
    List<ProjectScriptEpisode> findByScriptId(@Param("scriptId") long scriptId);

    /** 删除剧本全部分集。 */
    int deleteByScriptId(@Param("scriptId") long scriptId);
}
