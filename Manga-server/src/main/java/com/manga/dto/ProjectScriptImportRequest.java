package com.manga.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 承载全文或多章节异步导入请求。 */
public record ProjectScriptImportRequest(
        Long afterChapterId,
        @Size(max = 32) String sourceType,
        @Size(max = 20000000) String rawContent,
        @Valid List<Chapter> chapters
) {

    /** 显式提供的章节输入。 */
    public record Chapter(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 2000) String synopsis,
            @NotBlank @Size(max = 200000) String rawContent
    ) {
    }
}
