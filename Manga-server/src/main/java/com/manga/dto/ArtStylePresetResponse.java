package com.manga.dto;

/** 返回项目可选画风预设及其提示词参考信息。 */
public record ArtStylePresetResponse(
        /** 稳定唯一标识。 */
        String key,
        /** 画风预设名称。 */
        String name,
        /** 画风预设描述。 */
        String description,
        /** 画风英文图片生成提示词。 */
        String imagePrompt,
        /** 画风参考图路径。 */
        String referenceImagePath
) {
}
