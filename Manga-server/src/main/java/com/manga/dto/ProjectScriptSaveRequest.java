package com.manga.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.manga.common.constant.ValidationConstants.PROJECT_DESCRIPTION_MAX_LENGTH;

/** 承载人工编辑或导入后的结构化剧本。 */
public record ProjectScriptSaveRequest(
        /** 剧本标题。 */
        @NotBlank @Size(max = 120) String title,
        /** 剧本简介。 */
        @Size(max = PROJECT_DESCRIPTION_MAX_LENGTH) String synopsis,
        /** 剧本原始文本。 */
        @NotBlank String rawContent,
        /** 原始文本来源类型。 */
        @Size(max = 32) String sourceType,
        /** 分集列表。 */
        @Valid List<Episode> episodes
) {

    /** 描述剧本分集。 */
    public record Episode(
            /** 分集编号。 */
            Integer episodeNumber,
            /** 分集标题。 */
            @Size(max = 120) String title,
            /** 分集简介。 */
            @Size(max = 2000) String summary,
            /** 场景列表。 */
            @Valid List<Scene> scenes
    ) {
    }

    /** 描述剧本场景。 */
    public record Scene(
            /** 场景编号。 */
            Integer sceneNumber,
            /** 场景地点。 */
            @Size(max = 120) String location,
            /** 场景时间描述。 */
            @Size(max = 120) String time,
            /** 场景摘要。 */
            @Size(max = 2000) String summary,
            /** 场景画面内容。 */
            @Size(max = 5000) String content,
            /** 对白和旁白列表。 */
            @Valid List<Dialogue> dialogues
    ) {
    }

    /** 描述场景对白。 */
    public record Dialogue(
            /** 说话角色。 */
            @Size(max = 120) String speaker,
            /** 对白文本。 */
            @Size(max = 5000) String text,
            /** 对白类型。 */
            @Size(max = 20) String type
    ) {
    }
}
