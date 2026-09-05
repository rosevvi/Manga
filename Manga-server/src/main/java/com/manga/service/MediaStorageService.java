package com.manga.service;

import com.manga.common.constant.StorageConstants;
import com.manga.config.properties.AliyunOssProperties;
import com.manga.service.storage.StorageStrategy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 按默认存储配置选择策略，统一保存上传和生成产生的媒体文件。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MediaStorageService {

    private final AliyunOssProperties aliyunOssProperties;
    private final List<StorageStrategy> strategies;
    /** 按存储类型索引的策略注册表。 */
    private Map<String, StorageStrategy> strategyMap;

    /** 按类型注册可用媒体存储策略。 */
    @PostConstruct
    void initializeStrategyMap() {
        strategyMap = strategies.stream().collect(Collectors.toMap(StorageStrategy::getType, Function.identity()));
    }

    /** 保存二进制文件并返回可访问地址。 */
    public String storeBytes(byte[] data, String subDir, String extension) {
        StorageStrategy strategy = resolveStrategy();
        log.info("[MediaStorageService#storeBytes] request size={} subDir={} strategy={}",
                data.length, subDir, strategy.getType());
        return strategy.storeBytes(data, subDir, extension);
    }

    /** 按环境变量选择媒体存储策略。 */
    private StorageStrategy resolveStrategy() {
        if (aliyunOssProperties.enabled()) {
            StorageStrategy aliyunOss = strategyMap.get(StorageConstants.ALIYUN_OSS_TYPE);
            if (aliyunOss == null) {
                throw new IllegalStateException(StorageConstants.STORAGE_STRATEGY_UNAVAILABLE);
            }
            return aliyunOss;
        }
        StorageStrategy local = strategyMap.get(StorageConstants.LOCAL_TYPE);
        if (local == null) {
            throw new IllegalStateException(StorageConstants.STORAGE_STRATEGY_UNAVAILABLE);
        }
        return local;
    }
}
