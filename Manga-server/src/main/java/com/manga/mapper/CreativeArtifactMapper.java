package com.manga.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeArtifact;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
/** 简单平台产物 CRUD，由 Repository 统一使用。 */
public interface CreativeArtifactMapper extends BaseMapper<CreativeArtifact> {
    /** 保存最近一次采用请求及结果，内容和来源字段保持不可变。 */
    int recordAdoption(@Param("id") long id, @Param("requestRevision") long requestRevision,
            @Param("appliedRevision") long appliedRevision, @Param("at") LocalDateTime at,
            @Param("userId") long userId, @Param("actor") String actor);
}

