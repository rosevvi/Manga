package com.manga.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 将图片类型限制在对应资产类别内，并为新增图片提供默认名称。 */
@Getter
@RequiredArgsConstructor
public enum AssetVisualKind {
    FRONT(ProjectAssetKind.CHARACTER, "正面"),
    SIDE(ProjectAssetKind.CHARACTER, "侧面"),
    BACK(ProjectAssetKind.CHARACTER, "背面"),
    TURNAROUND(ProjectAssetKind.CHARACTER, "三视图"),
    EXPRESSIONS(ProjectAssetKind.CHARACTER, "表情集"),
    STATE(ProjectAssetKind.CHARACTER, "状态图"),
    CONTEXT(ProjectAssetKind.CHARACTER, "角色场景图"),
    SCENE_DESIGN(ProjectAssetKind.SCENE, "场景设定图"),
    PROP_DESIGN(ProjectAssetKind.PROP, "道具设定图");

    private final ProjectAssetKind assetKind;
    private final String displayName;
}
