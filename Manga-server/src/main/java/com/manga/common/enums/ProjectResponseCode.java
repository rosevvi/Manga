package com.manga.common.enums;

import com.manga.common.api.ResponseCode;

/** 定义项目与分镜模块的业务响应码。 */
public enum ProjectResponseCode implements ResponseCode {
    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND", "项目不存在或无权访问"),
    STORYBOARD_SHOT_NOT_FOUND("STORYBOARD_SHOT_NOT_FOUND", "分镜不存在或无权访问"),
    GUEST_OPERATION_FORBIDDEN("GUEST_OPERATION_FORBIDDEN", "游客账号不能管理项目"),
    STORYBOARD_ORDER_INVALID("STORYBOARD_ORDER_INVALID", "分镜排序数据与当前项目不一致");

    private final String code;
    private final String message;

    ProjectResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
