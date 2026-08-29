package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.AiProviderConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 定义用户 AI 服务配置的数据访问契约。 */
public interface AiProviderConfigMapper extends BaseMapper<AiProviderConfig> {

    List<AiProviderConfig> findAllOwnedBy(@Param("ownerUserId") long ownerUserId);

    AiProviderConfig findOwnedById(@Param("configId") long configId, @Param("ownerUserId") long ownerUserId);

    AiProviderConfig findDefaultOwnedBy(@Param("ownerUserId") long ownerUserId);

    int countOwnedByName(
            @Param("ownerUserId") long ownerUserId,
            @Param("name") String name,
            @Param("excludedConfigId") Long excludedConfigId);

    int updateOwned(@Param("config") AiProviderConfig config, @Param("ownerUserId") long ownerUserId);

    int clearDefault(@Param("ownerUserId") long ownerUserId, @Param("actor") String actor);

    int setDefault(
            @Param("configId") long configId,
            @Param("ownerUserId") long ownerUserId,
            @Param("actor") String actor);
}
