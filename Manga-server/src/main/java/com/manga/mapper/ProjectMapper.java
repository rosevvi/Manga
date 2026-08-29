package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.MangaProject;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 定义项目及其分镜数量摘要的数据访问契约。 */
public interface ProjectMapper extends BaseMapper<MangaProject> {

    List<MangaProject> findAllOwnedBy(@Param("ownerUserId") long ownerUserId);

    MangaProject findOwnedById(@Param("projectId") long projectId, @Param("ownerUserId") long ownerUserId);

    int updateOwned(@Param("project") MangaProject project, @Param("ownerUserId") long ownerUserId);
}
