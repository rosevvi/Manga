package com.manga.service;

import com.manga.entity.StorageConfig;
import com.manga.repository.StorageConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** 读取平台默认媒体存储配置，未配置时由存储策略回退到本地配置。 */
@Service
@RequiredArgsConstructor
public class StorageConfigService {

    private final StorageConfigRepository storageConfigRepository;

    /** 查询默认且启用的媒体存储配置。 */
    public Optional<StorageConfig> findDefaultConfig() {
        return storageConfigRepository.findDefaultEnabled();
    }
}
