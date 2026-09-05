package com.manga.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 承载 AI 剧本生成的创作要求。 */
public record ProjectScriptGenerateRequest(
        /** 用户对剧本的创作要求。 */
        @NotBlank @Size(max = 20000) String prompt
) {
}
