package com.manga.common.enums;

import com.manga.common.api.ResponseCode;
import lombok.RequiredArgsConstructor;

/** 项目资产和图片的公开业务错误。 */
@RequiredArgsConstructor
public enum ProjectAssetError implements ResponseCode {
    NOT_FOUND("PROJECT_ASSET_NOT_FOUND", "资产或图片不存在"),
    INVALID_VISUAL("PROJECT_ASSET_VISUAL_INVALID", "图片类型或生成设置无效"),
    INVALID_REFERENCE("PROJECT_ASSET_REFERENCE_INVALID", "请选择当前资产下已生成的其他图片作为参考图"),
    NOT_IN_CHAPTER("PROJECT_ASSET_NOT_IN_CHAPTER", "只能采用镜头所属章节里的资产图"),
    VISUAL_REPLACED("PROJECT_ASSET_VISUAL_REPLACED", "资产图片已更新，请重新选择"),
    EXTRACTION_NOT_SUBMITTED("CHAPTER_ASSETS_NOT_SUBMITTED", "模型未提交本章资产识别结果，可重新识别"),
    INVALID_MATCH("CHAPTER_ASSET_INVALID_MATCH", "已有资产名称不唯一或选择无效，请从项目目录指定对应类别的资产 ID"),
    GENERATING("PROJECT_ASSET_GENERATING", "这张图片已有生成任务，请等待完成后再操作"),
    NO_ARTIFACT("PROJECT_ASSET_NO_ARTIFACT", "请先生成图片");

    private final String code;
    private final String message;

    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
