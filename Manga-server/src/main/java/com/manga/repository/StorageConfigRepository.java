package com.manga.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.manga.entity.StorageConfig;
import com.manga.mapper.StorageConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 使用 MyBatis-Plus 查询媒体存储配置。 */
@Repository
@RequiredArgsConstructor
public class StorageConfigRepository {

    private final StorageConfigMapper storageConfigMapper;

    /** 查询默认且启用的存储配置。 */
    public Optional<StorageConfig> findDefaultEnabled() {
        return Optional.ofNullable(storageConfigMapper.selectOne(
                new LambdaQueryWrapper<StorageConfig>()
                        .eq(StorageConfig::getDefaultConfig, true)
                        .eq(StorageConfig::getEnabled, true)
                        .last("LIMIT 1")));
    }
}
