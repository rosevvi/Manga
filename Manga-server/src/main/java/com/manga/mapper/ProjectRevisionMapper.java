package com.manga.mapper;

import com.manga.entity.MangaProject;
import org.apache.ibatis.annotations.Param;

/** 项目聚合锁与版本围栏，不执行内容写入。 */
public interface ProjectRevisionMapper {
    /** 锁定项目所有者行，所有章节和镜头写入共用此锁。 */
    MangaProject lockOwned(@Param("projectId") long projectId, @Param("userId") long userId);
    /** 以期望版本原子推进项目 revision。 */
    int advanceProject(@Param("projectId") long projectId, @Param("revision") long revision, @Param("actor") String actor);
    /** 在项目锁内推进章节版本，归属通过关联剧本校验。 */
    int advanceChapter(@Param("projectId") long projectId, @Param("chapterId") long chapterId, @Param("actor") String actor);
}
