package com.manga.service.storage;

import com.manga.config.properties.MediaUploadProperties;
import com.manga.common.constant.StorageConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.LinkOption;
import java.util.Objects;
import java.util.UUID;

import static com.manga.common.constant.StorageConstants.LOCAL_TYPE;
import static com.manga.common.constant.StorageConstants.DEFAULT_UPLOAD_DIRECTORY;
import static com.manga.common.constant.StorageConstants.STORAGE_FILE_SAVE_FAILED;

/** 将媒体文件保存到后端本地磁盘，并通过静态资源路径访问。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LocalStorageStrategy implements StorageStrategy {

    private final MediaUploadProperties uploadProperties;

    /** 返回本地存储策略类型。 */
    @Override
    public String getType() {
        return LOCAL_TYPE;
    }

    /** 将媒体二进制数据保存到本地磁盘。 */
    @Override
    public String storeBytes(byte[] data, String subDir, String extension) {
        return storeNamedBytes(data, subDir, extension, UUID.randomUUID().toString());
    }

    /** 以经过校验的稳定名称落盘；路径必须位于配置的媒体根目录内。 */
    @Override
    public String storeNamedBytes(byte[] data, String subDir, String extension, String stableName) {
        if (!stableName.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException(STORAGE_FILE_SAVE_FAILED);
        Path baseDirectory = resolveBaseDirectory();
        String filename = stableName + normalizeExtension(extension);
        Path targetDirectory = baseDirectory.resolve(normalizeSubDir(subDir)).normalize().toAbsolutePath();
        Path targetPath = targetDirectory.resolve(filename).normalize();
        if (!targetDirectory.startsWith(baseDirectory) || !targetPath.startsWith(targetDirectory)) {
            throw new IllegalArgumentException(STORAGE_FILE_SAVE_FAILED);
        }

        Path temporary = null;
        try {
            Files.createDirectories(targetDirectory);
            boolean generated = StorageConstants.GENERATED_DIRECTORY.equals(subDir);
            if (generated && Files.isSymbolicLink(targetDirectory)) throw new IllegalArgumentException(StorageConstants.GENERATED_REFERENCE_INVALID);
            // 生成对象的临时文件也有可推导名称，进程崩溃后能够按登记记录回收。
            temporary = generated ? targetDirectory.resolve(filename + ".part") : Files.createTempFile(targetDirectory, filename, ".part");
            Files.write(temporary, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            try { Files.move(temporary, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, targetPath, StandardCopyOption.REPLACE_EXISTING); }
            log.info("[LocalStorageStrategy#storeBytes] stored path={} size={}", targetPath, data.length);
            return normalizePublicPath() + "/" + normalizeSubDir(subDir) + "/" + filename;
        } catch (IOException exception) {
            throw new IllegalStateException(STORAGE_FILE_SAVE_FAILED, exception);
        } finally {
            if (Objects.nonNull(temporary)) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException exception) { log.warn("Media temporary file cleanup deferred filename={}", filename); }
            }
        }
    }

    /** 解析本地存储根目录。 */
    private Path resolveBaseDirectory() {
        return uploadProperties.directory().normalize().toAbsolutePath();
    }

    /** 位置只由当前配置根目录、固定生成目录和服务端代次名称构造。 */
    @Override
    public GeneratedMediaReference generatedReference(String jobId, long epoch, GeneratedMediaFormat format) {
        String name = GeneratedMediaNames.of(jobId, epoch);
        return new GeneratedMediaReference(getType(), generatedNamespace(), name,
                normalizePublicPath() + "/" + StorageConstants.GENERATED_DIRECTORY + "/" + name + "." + format.extension(), format);
    }
    /** 同代次只覆盖同一文件，发布前仍需数据库确认当前执行权。 */
    @Override
    public void writeGenerated(byte[] data, GeneratedMediaReference reference) {
        GeneratedMediaNames.require(reference, getType(), generatedNamespace());
        storeNamedBytes(data, StorageConstants.GENERATED_DIRECTORY, reference.format().extension(), reference.name());
    }
    /** 只删除固定生成目录内登记的普通文件，不递归删除目录。 */
    @Override
    public void deleteGenerated(GeneratedMediaReference reference) {
        GeneratedMediaNames.require(reference, getType(), generatedNamespace());
        Path directory = resolveBaseDirectory().resolve(StorageConstants.GENERATED_DIRECTORY).normalize();
        Path target = directory.resolve(reference.name() + "." + reference.format().extension()).normalize();
        if (!target.getParent().equals(directory) || Files.isSymbolicLink(directory)) throw new IllegalArgumentException(StorageConstants.GENERATED_REFERENCE_INVALID);
        try { Files.deleteIfExists(target); Files.deleteIfExists(target.resolveSibling(target.getFileName() + ".part")); }
        catch (IOException exception) { throw new IllegalStateException(STORAGE_FILE_SAVE_FAILED, exception); }
    }
    /** 固定目录 URI 的末尾斜杠，避免目录创建前后 Path.toUri() 变化导致重启后无法回收。 */
    private String generatedNamespace() {
        return GeneratedMediaNames.namespace(resolveBaseDirectory().toUri().toString().replaceAll("/+$", ""));
    }

    /** 规范媒体公开访问路径。 */
    private String normalizePublicPath() {
        String configured = uploadProperties.publicPath();
        String normalized = configured.trim().replaceAll("/+$", "");
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    /** 规范媒体存储子目录。 */
    private String normalizeSubDir(String subDir) {
        String normalized = subDir == null || subDir.isBlank() ? DEFAULT_UPLOAD_DIRECTORY : subDir.trim();
        return normalized.replace("\\", "/").replaceAll("^/+", "").replaceAll("/+$", "");
    }

    /** 规范媒体文件扩展名。 */
    private String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return ".bin";
        }
        return extension.startsWith(".") ? extension : "." + extension;
    }
}
