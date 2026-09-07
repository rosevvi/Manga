package com.manga.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.common.enums.TaskStatus;
import com.manga.common.enums.TaskType;
import com.manga.config.properties.ScriptTaskProperties;
import com.manga.dto.ProjectScriptImportRequest;
import com.manga.dto.TaskResponse;
import com.manga.entity.MangaTask;
import com.manga.entity.MangaTaskUnit;
import com.manga.entity.ProjectScriptChapter;
import com.manga.repository.MangaTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharsetDecoder;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

import static com.manga.common.constant.MediaUploadConstants.*;

/** 管理章节导入任务的持久化、执行和恢复。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptTaskService {

    private static final Pattern CHAPTER_PATTERN = Pattern.compile("(?m)^\\s*(?:第\\s*[0-9一二三四五六七八九十百千]+\\s*[章节]|Chapter\\s+\\d+|第一幕|第二幕|第三幕).*$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> SUPPORTED_SCRIPT_EXTENSIONS = Set.of("txt", "md");

    private final MangaTaskRepository taskRepository;
    private final ProjectScriptService scriptService;
    private final ScriptTaskProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    @Qualifier("applicationTaskExecutor")
    private final Executor executor;

    /** 创建并异步执行全文或多章节导入任务。 */
    @Transactional
    public TaskResponse submitImport(long projectId, ProjectScriptImportRequest request,
            long ownerUserId, String actor) {
        List<ProjectScriptImportRequest.Chapter> chapters = request.chapters();
        String rawContent = request.rawContent();
        if ((chapters == null || chapters.isEmpty()) && (rawContent == null || rawContent.isBlank())) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR, "rawContent or chapters is required",
                    HttpStatus.BAD_REQUEST);
        }
        try {
            String payload = chapters != null && !chapters.isEmpty()
                    ? objectMapper.writeValueAsString(chapters) : rawContent;
            if (payload.getBytes(StandardCharsets.UTF_8).length > properties.maxInputSize()) {
                throw new BusinessException(CommonResponseCode.VALIDATION_ERROR, UPLOAD_REQUEST_TOO_LARGE,
                        HttpStatus.BAD_REQUEST);
            }
            return submitImportPayload(projectId, payload, request.sourceType(), request.afterChapterId(),
                    chapters == null ? 0 : chapters.size(), ownerUserId, actor);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist script import input", exception);
        }
    }

    /** 接收上传文件并异步执行全文导入任务。 */
    @Transactional
    public TaskResponse submitImport(long projectId, MultipartFile file, String sourceType, Long afterChapterId,
            long ownerUserId, String actor) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR, UPLOAD_SCRIPT_EMPTY, HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > properties.maxInputSize()) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR, UPLOAD_REQUEST_TOO_LARGE, HttpStatus.BAD_REQUEST);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = requireSupportedScriptExtension(originalFilename);
        String payload = readUtf8Text(file);
        String normalizedSourceType = normalizeSourceType(sourceType, extension);
        try {
            return submitImportPayload(projectId, payload, normalizedSourceType, afterChapterId, 0, ownerUserId, actor);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist script import input", exception);
        }
    }

    /** 查询任务。 */
    public MangaTask requireOwned(long taskId, long ownerUserId) {
        return taskRepository.findById(taskId).filter(task -> task.getOwnerUserId().equals(ownerUserId))
                .orElseThrow(() -> new IllegalArgumentException("task not found"));
    }

    /** 返回当前用户有权访问的任务摘要。 */
    public TaskResponse toResponseForUser(long taskId, long ownerUserId) {
        return toResponse(requireOwned(taskId, ownerUserId));
    }

    /** 查询当前用户任务列表。 */
    public List<TaskResponse> list(long ownerUserId, boolean activeOnly) {
        return (activeOnly ? taskRepository.findActive(ownerUserId) : taskRepository.findByOwner(ownerUserId))
                .stream().map(this::toResponse).toList();
    }

    /** 执行可恢复导入任务。 */
    public void execute(long taskId, long ownerUserId, String actor) {
        MangaTask task = requireOwned(taskId, ownerUserId);
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(LocalDateTime.now());
        task.setAttemptCount(task.getAttemptCount() == null ? 1 : task.getAttemptCount() + 1);
        task.setUpdatedBy(actor);
        taskRepository.update(task);
        cacheProgress(task);
        try {
            String payload = Files.readString(Paths.get(task.getSourceReference()), StandardCharsets.UTF_8);
            List<ProjectScriptImportRequest.Chapter> chapters = parseChapters(payload);
            task.setTotalUnits(chapters.size());
            taskRepository.update(task);
            cacheProgress(task);
            int sortOrder = scriptService.prepareInsertOrder(task.getProjectId(), task.getAfterChapterId(), chapters.size(), ownerUserId);
            for (int index = 0; index < chapters.size(); index++) {
                ProjectScriptImportRequest.Chapter input = chapters.get(index);
                task.setCurrentUnit(input.title());
                taskRepository.update(task);
                cacheProgress(task);
                try {
                    ProjectScriptChapter chapter = scriptService.createTaskChapter(task.getProjectId(), input.title(),
                            input.synopsis(), input.rawContent(), task.getSourceContentType(), sortOrder++, ownerUserId, actor);
                    taskRepository.createUnit(MangaTaskUnit.builder().taskId(taskId)
                            .unitIndex(index).chapterId(chapter.getId()).chapterTitle(input.title())
                            .status(TaskStatus.COMPLETED).startedAt(LocalDateTime.now()).finishedAt(LocalDateTime.now()).build());
                    task.setCompletedUnits(task.getCompletedUnits() + 1);
                } catch (RuntimeException exception) {
                    task.setFailedUnits(task.getFailedUnits() + 1);
                    task.setLastError(exception.getMessage());
                    taskRepository.createUnit(MangaTaskUnit.builder().taskId(taskId).unitIndex(index)
                            .chapterTitle(input.title()).status(TaskStatus.FAILED).errorMessage(exception.getMessage())
                            .finishedAt(LocalDateTime.now()).build());
                }
                taskRepository.update(task);
                cacheProgress(task);
            }
            task.setStatus(task.getFailedUnits() > 0 ? TaskStatus.PARTIAL_FAILED : TaskStatus.COMPLETED);
            task.setFinishedAt(LocalDateTime.now());
            task.setCurrentUnit(null);
            taskRepository.update(task);
            cacheProgress(task);
        } catch (Exception exception) {
            task.setStatus(TaskStatus.FAILED);
            task.setLastError(exception.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            taskRepository.update(task);
            cacheProgress(task);
            log.warn("Script import task failed taskId={} projectId={}", taskId, task.getProjectId());
        } finally {
            try { Files.deleteIfExists(Paths.get(task.getSourceReference())); }
            catch (IOException exception) { log.warn("Script import temp file cleanup failed taskId={}", taskId); }
        }
    }

    /** 将恢复任务重新提交到应用线程池。 */
    public void resume(MangaTask task) {
        executor.execute(() -> execute(task.getId(), task.getOwnerUserId(), task.getUpdatedBy()));
    }

    private TaskResponse submitImportPayload(long projectId, String payload, String sourceType, Long afterChapterId,
            int totalUnits, long ownerUserId, String actor) throws IOException {
        Path directory = Paths.get(properties.inputDirectory()).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, "script-task-", ".txt");
        boolean committed = false;
        try {
            Files.writeString(file, payload, StandardCharsets.UTF_8);
            MangaTask task = taskRepository.create(MangaTask.builder()
                    .ownerUserId(ownerUserId).projectId(projectId).taskType(TaskType.SCRIPT_IMPORT)
                    .status(TaskStatus.QUEUED).title("剧本章节导入").sourceReference(file.toString())
                    .sourceSha256(sha256(payload)).sourceSize((long) payload.getBytes(StandardCharsets.UTF_8).length)
                    .sourceContentType(sourceType).afterChapterId(afterChapterId)
                    .totalUnits(totalUnits).completedUnits(0).failedUnits(0)
                    .attemptCount(0).createdBy(actor).updatedBy(actor).build());
            executor.execute(() -> execute(task.getId(), ownerUserId, actor));
            cacheProgress(task);
            committed = true;
            return toResponse(task);
        } finally {
            if (!committed) {
                deleteTempFile(file);
            }
        }
    }

    private List<ProjectScriptImportRequest.Chapter> parseChapters(String payload) throws IOException {
        if (payload.trim().startsWith("[")) {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        }
        String[] pieces = CHAPTER_PATTERN.split(payload);
        if (pieces.length <= 1) {
            return List.of(new ProjectScriptImportRequest.Chapter("未命名章节", null, payload));
        }
        String[] headers = CHAPTER_PATTERN.matcher(payload).results().map(match -> match.group()).toArray(String[]::new);
        List<ProjectScriptImportRequest.Chapter> chapters = new ArrayList<>();
        for (int index = 1; index < pieces.length; index++) {
            String content = pieces[index].trim();
            if (!content.isBlank()) chapters.add(new ProjectScriptImportRequest.Chapter(headers[index - 1].trim(), null, content));
        }
        return chapters.isEmpty() ? List.of(new ProjectScriptImportRequest.Chapter("未命名章节", null, payload)) : chapters;
    }

    private TaskResponse toResponse(MangaTask task) {
        return new TaskResponse(task.getId(), task.getProjectId(), task.getTaskType(), task.getStatus(), task.getTitle(),
                task.getTotalUnits() == null ? 0 : task.getTotalUnits(), task.getCompletedUnits() == null ? 0 : task.getCompletedUnits(),
                task.getFailedUnits() == null ? 0 : task.getFailedUnits(), task.getCurrentUnit(), task.getLastError(),
                task.getCreatedAt(), task.getStartedAt(), task.getFinishedAt());
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private String normalizeSourceType(String sourceType, String extension) {
        if (StringUtils.hasText(sourceType)) {
            return sourceType.trim();
        }
        return "md".equalsIgnoreCase(extension) ? "MARKDOWN" : "TXT";
    }

    private String requireSupportedScriptExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR, UPLOAD_SCRIPT_EMPTY, HttpStatus.BAD_REQUEST);
        }
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (!StringUtils.hasText(extension)) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR, UPLOAD_SCRIPT_TYPE_UNSUPPORTED, HttpStatus.BAD_REQUEST);
        }
        String normalizedExtension = extension.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_SCRIPT_EXTENSIONS.contains(normalizedExtension)) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR, UPLOAD_SCRIPT_TYPE_UNSUPPORTED, HttpStatus.BAD_REQUEST);
        }
        return normalizedExtension;
    }

    private String readUtf8Text(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return decodeUtf8(inputStream.readAllBytes());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read uploaded script file", exception);
        }
    }

    private String decodeUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new BusinessException(CommonResponseCode.VALIDATION_ERROR, UPLOAD_SCRIPT_ENCODING_UNSUPPORTED,
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void deleteTempFile(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("Script import temp file cleanup failed path={}", file);
        }
    }

    private void cacheProgress(MangaTask task) {
        String key = "manga:task:" + task.getId();
        redisTemplate.opsForHash().put(key, "status", task.getStatus().name());
        redisTemplate.opsForHash().put(key, "completedUnits", String.valueOf(task.getCompletedUnits()));
        redisTemplate.opsForHash().put(key, "totalUnits", String.valueOf(task.getTotalUnits()));
        if (task.getCurrentUnit() != null) redisTemplate.opsForHash().put(key, "currentUnit", task.getCurrentUnit());
        redisTemplate.expire(key, Duration.ofDays(1));
    }
}
