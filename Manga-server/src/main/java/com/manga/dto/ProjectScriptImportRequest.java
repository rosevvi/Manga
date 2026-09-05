package com.manga.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 承载 TXT 或 Markdown 剧本导入内容。 */
public record ProjectScriptImportRequest(
        /** 导入的原始文本。 */
        @NotBlank @Size(max = 200000) String rawContent,
        /** 导入来源类型。 */
        @Size(max = 32) String sourceType
) {
}
