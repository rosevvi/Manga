package com.manga.common.enums;

import com.manga.common.api.ResponseCode;

/** 定义项目与分镜模块的业务响应码。 */
public enum ProjectResponseCode implements ResponseCode {
    /** 项目不存在或无权访问。 */
    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND", "项目不存在或无权访问"),
    /** 项目画面比例不受支持。 */
    PROJECT_ASPECT_RATIO_INVALID("PROJECT_ASPECT_RATIO_INVALID", "项目画面比例不受支持"),
    /** 项目画风预设不受支持。 */
    PROJECT_ART_STYLE_INVALID("PROJECT_ART_STYLE_INVALID", "项目画风预设不受支持"),
    /** 分镜镜头不存在或无权访问。 */
    STORYBOARD_SHOT_NOT_FOUND("STORYBOARD_SHOT_NOT_FOUND", "分镜不存在或无权访问"),
    /** 游客不能管理项目。 */
    GUEST_OPERATION_FORBIDDEN("GUEST_OPERATION_FORBIDDEN", "游客账号不能管理项目"),
    /** 分镜排序数据不一致。 */
    STORYBOARD_ORDER_INVALID("STORYBOARD_ORDER_INVALID", "分镜排序数据与当前项目不一致"),
    /** 剧本结构不符合当前版本契约。 */
    SCRIPT_INVALID("SCRIPT_INVALID", "剧本结构不符合要求");

    /** 稳定业务编码。 */
    private final String code;
    /** 结果提示信息。 */
    private final String message;

    /** 初始化枚举项元数据。 */
    ProjectResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 返回响应编码。 */
    @Override
    public String code() {
        return code;
    }

    /** 返回响应描述。 */
    @Override
    public String message() {
        return message;
    }
}
