package com.manga.service;

import com.manga.common.constant.StorageConstants;
import com.manga.config.properties.AliyunOssProperties;
import com.manga.service.storage.StorageStrategy;
import com.manga.service.storage.AliyunOssStorageStrategy;
import com.manga.service.storage.GeneratedMediaReference;
import java.util.Objects;
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
    private final AliyunOssStorageStrategy aliyunOss;
    private final List<StorageStrategy> strategies;
    /** 按存储类型索引的策略注册表。 */
    private Map<String, StorageStrategy> strategyMap;

    /** 按类型注册可用媒体存储策略。 */
    @PostConstruct
    void initializeStrategyMap() {
        strategyMap = strategies.stream().collect(Collectors.toMap(StorageStrategy::getType, Function.identity()));
    }

    /** 保存用户图片，OSS 使用上传人目录以限定后续预览授权。 */
    public String storeUserImage(long userId, byte[] data, String subDir, String extension) {
        StorageStrategy strategy = resolveStrategy();
        log.info("[MediaStorageService#storeUserImage] request size={} subDir={} strategy={}",
                data.length, subDir, strategy.getType());
        if (StorageConstants.ALIYUN_OSS_TYPE.equals(strategy.getType())) {
            return aliyunOss.storeUserImage(userId, data, subDir, extension);
        }
        return strategy.storeBytes(data, subDir, extension);
    }

    /** 为调用方已授权的用户图片生成短期链接，外链与本地路径直接返回。 */
    public String previewUserImage(String url, long uploadOwnerId) {
        return aliyunOss.previewUserImage(url, uploadOwnerId);
    }

    /** 为调用方从受保护产物记录取得的地址生成短期预览链接。 */
    public String previewGeneratedImage(String url) {
        return aliyunOss.previewGeneratedImage(url);
    }

    /** 先生成位置并登记，再允许执行物理写入。 */
    public GeneratedMediaReference prepareGenerated(String jobId, long epoch) { return resolveStrategy().generatedReference(jobId, epoch); }
    /** 写入已登记位置，禁止客户端上传任意删除或写入地址。 */
    public void storeGeneratedBytes(byte[] data, GeneratedMediaReference reference) { requireStrategy(reference).writeGenerated(data, reference); }
    /** 回收只操作引用指定的策略，不跟随当前默认存储切换。 */
    public void deleteGenerated(GeneratedMediaReference reference) { requireStrategy(reference).deleteGenerated(reference); }
    /** 解析登记的存储策略，未知策略交由退避处理。 */
    private StorageStrategy requireStrategy(GeneratedMediaReference reference) {
        StorageStrategy strategy = strategyMap.get(reference.storageType());
        if (Objects.isNull(strategy)) throw new IllegalStateException(StorageConstants.STORAGE_STRATEGY_UNAVAILABLE);
        return strategy;
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
