package com.manga.service.storage;

import com.manga.common.constant.StorageConstants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** 生成对象的独立命名与删除边界，避免旧 worker 写入已发布的新代次对象。 */
public final class GeneratedMediaNames {
    /** 只允许平台作业 UUID 与正整数执行代次。 */
    private static final Pattern NAME = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-[1-9][0-9]*");
    private GeneratedMediaNames() { }
    /** 每个写入代次使用不同对象名，同一代次重放保持幂等。 */
    public static String of(String jobId, long epoch) {
        if (epoch < 1 || !UUID.fromString(jobId).toString().equals(jobId)) throw new IllegalArgumentException(StorageConstants.GENERATED_REFERENCE_INVALID);
        return jobId + "-" + epoch;
    }
    /** 把不含凭据的物理位置转为命名空间标识。 */
    public static String namespace(String location) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(location.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(StorageConstants.GENERATED_REFERENCE_INVALID, exception); }
    }
    /** 配置变更后不允许把旧引用误用于新的磁盘目录或 Bucket。 */
    public static void require(GeneratedMediaReference reference, String type, String namespace) {
        if (Objects.isNull(reference) || !Objects.equals(reference.storageType(), type)
                || !Objects.equals(reference.namespace(), namespace) || Objects.isNull(reference.name())
                || !NAME.matcher(reference.name()).matches()) throw new IllegalArgumentException(StorageConstants.GENERATED_REFERENCE_INVALID);
    }
}
