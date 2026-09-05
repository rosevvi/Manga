package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.ProjectScriptDialogue;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 定义剧本对白的数据访问契约。 */
public interface ProjectScriptDialogueMapper extends BaseMapper<ProjectScriptDialogue> {

    /** 查询剧本全部对白。 */
    List<ProjectScriptDialogue> findByScriptId(@Param("scriptId") long scriptId);

    /** 删除剧本全部对白。 */
    int deleteByScriptId(@Param("scriptId") long scriptId);
}
