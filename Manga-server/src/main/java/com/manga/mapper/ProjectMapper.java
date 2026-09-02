package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.MangaProject;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 定义项目及其分镜数量摘要的数据访问契约。 */
public interface ProjectMapper extends BaseMapper<MangaProject> {

    /** 查询用户拥有的项目列表。 */
    List<MangaProject> findAllOwnedBy(@Param("ownerUserId") long ownerUserId);

    /** 查询用户可访问的项目列表。 */
    List<MangaProject> findAllAccessibleBy(@Param("userId") long userId);

    /** 按 ID 查询用户拥有的项目。 */
    MangaProject findOwnedById(@Param("projectId") long projectId, @Param("ownerUserId") long ownerUserId);

    /** 按 ID 查询用户可访问的项目。 */
    MangaProject findAccessibleById(@Param("projectId") long projectId, @Param("userId") long userId);

    /** 更新用户拥有的项目。 */
    int updateOwned(@Param("project") MangaProject project, @Param("ownerUserId") long ownerUserId);
}
