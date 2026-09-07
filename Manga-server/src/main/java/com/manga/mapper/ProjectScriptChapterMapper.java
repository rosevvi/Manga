package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.ProjectScriptChapter;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 定义剧本章节的数据访问契约。 */
public interface ProjectScriptChapterMapper extends BaseMapper<ProjectScriptChapter> {

    List<ProjectScriptChapter> findSummaries(@Param("scriptId") long scriptId);

    ProjectScriptChapter findByIdAndScript(@Param("chapterId") long chapterId, @Param("scriptId") long scriptId);

    int shiftSortOrders(@Param("scriptId") long scriptId, @Param("sortOrder") int sortOrder,
            @Param("delta") int delta);

    int updateChapter(@Param("chapter") ProjectScriptChapter chapter);
}
