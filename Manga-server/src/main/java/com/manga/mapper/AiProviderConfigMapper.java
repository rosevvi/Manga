package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.AiProviderConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 定义用户 AI 服务配置的数据访问契约。 */
public interface AiProviderConfigMapper extends BaseMapper<AiProviderConfig> {

    /** 查询用户拥有的AI 服务配置列表。 */
    List<AiProviderConfig> findAllOwnedBy(@Param("ownerUserId") long ownerUserId);

    /** 按 ID 查询用户拥有的AI 服务配置。 */
    AiProviderConfig findOwnedById(@Param("configId") long configId, @Param("ownerUserId") long ownerUserId);

    /** 查询用户默认的AI 服务配置。 */
    AiProviderConfig findDefaultOwnedBy(@Param("ownerUserId") long ownerUserId);

    /** 统计用户同名的AI 服务配置数量。 */
    int countOwnedByName(
            @Param("ownerUserId") long ownerUserId,
            @Param("name") String name,
            @Param("excludedConfigId") Long excludedConfigId);

    /** 更新用户拥有的AI 服务配置。 */
    int updateOwned(@Param("config") AiProviderConfig config, @Param("ownerUserId") long ownerUserId);

    /** 清除用户AI 服务配置的默认标记。 */
    int clearDefault(@Param("ownerUserId") long ownerUserId, @Param("actor") String actor);

    /** 设置用户默认的AI 服务配置。 */
    int setDefault(
            @Param("configId") long configId,
            @Param("ownerUserId") long ownerUserId,
            @Param("actor") String actor);
}
