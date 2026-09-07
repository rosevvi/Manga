package com.manga.service;

import com.manga.common.enums.ProjectResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.dto.ProjectScriptGenerateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/** 提供章节级 AI 生成扩展点，具体长任务统一交由任务中心执行。 */
@Service
public class ProjectScriptGenerationService {

    /** 保留旧流式入口，提示调用方使用章节任务接口。 */
    public Flux<ServerSentEvent<String>> generate(long projectId, ProjectScriptGenerateRequest request) {
        throw new BusinessException(ProjectResponseCode.SCRIPT_INVALID,
                "请使用章节生成任务接口", HttpStatus.BAD_REQUEST);
    }
}
