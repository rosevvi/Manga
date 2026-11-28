package com.manga.dto;

import com.manga.agent.generation.GenerationProviderKey;
import com.manga.common.enums.AssetVisualKind;
import com.manga.common.enums.ProjectAssetKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 项目资产卡片、图片表单和章节资产列表的接口字段。 */
public final class ProjectAssetContracts {
    private ProjectAssetContracts() {}

    public static final int MAX_REFERENCES = 16;

    public record Fields(
            @NotBlank @Size(max = 120) String displayName,
            @Size(max = 2000) String overview,
            @Size(max = 40) String roleLabel,
            @Size(max = 300) String identityDescription,
            @Size(max = 40) String ageGroup,
            @Size(max = 800) String appearanceDescription,
            @Size(max = 800) String costumeDescription,
            @Size(max = 300) String locationDescription,
            @Size(max = 800) String environmentDescription,
            @Size(max = 300) String lightingDescription,
            @Size(max = 300) String materialDescription) {}

    /** Agent 识别的一项资产。已存在的对象只建立章节引用。 */
    public record RecognizedAsset(@NotNull ProjectAssetKind assetKind, @Positive Long existingAssetId,
                                  @Valid @NotNull Fields fields) {}

    public record CreateAsset(@NotNull ProjectAssetKind assetKind, @Positive Long chapterId, @Valid @NotNull Fields fields) {}

    public record SaveVisual(
            @NotNull AssetVisualKind visualKind, @Size(max = 120) String displayName,
            @Size(max = 500) String prompt, @Size(max = 300) String negativePrompt,
            @Positive Integer imageWidth, @Positive Integer imageHeight,
            GenerationProviderKey provider, @Positive Long modelConfigId, @Size(max = 100) String modelName,
            @Size(max = MAX_REFERENCES) List<@NotNull @Positive Long> referenceVisualIds) {}

    public record AssetSummary(long id, ProjectAssetKind assetKind, String displayName) {}

    /** 采用用户实际看到的当前图片，防止弹窗打开后替换成另一张。 */
    public record ShotAssetImage(@Positive long visualId, @Positive long artifactId, @Positive long expectedProjectRevision) {}

    public record Asset(long id, ProjectAssetKind assetKind, Fields fields, Long primaryVisualId, List<Visual> visuals) {}

    public record Visual(long id, AssetVisualKind visualKind, String displayName, String prompt, String negativePrompt,
                         Integer imageWidth, Integer imageHeight, GenerationProviderKey provider, Long modelConfigId,
                         String modelName, List<Long> referenceVisualIds, Long currentArtifactId,
                         String previewUrl, LocalDateTime generatedAt, boolean busy) {}
}
