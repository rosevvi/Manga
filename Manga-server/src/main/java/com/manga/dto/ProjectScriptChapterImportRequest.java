package com.manga.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 承载单章节同步导入请求。 */
public record ProjectScriptChapterImportRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String synopsis,
        @NotBlank @Size(max = 200000) String rawContent,
        @Size(max = 32) String sourceType
) {
}
