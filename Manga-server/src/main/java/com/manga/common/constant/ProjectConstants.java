package com.manga.common.constant;

import java.util.Set;

/** 维护项目模块的可复用配置、默认值和业务消息。 */
public final class ProjectConstants {

    /** 项目默认画面比例。 */
    public static final String DEFAULT_ASPECT_RATIO = "16:9";
    /** 自定义画风固定标识。 */
    public static final String CUSTOM_ART_STYLE_KEY = "custom";
    /** 内置画风参考图路径前缀。 */
    public static final String ART_STYLE_IMAGE_PATH_PREFIX = "/assets/art-styles/";
    /** 项目支持的画面比例集合。 */
    public static final Set<String> SUPPORTED_ASPECT_RATIOS = Set.of("16:9", "9:16", "1:1", "4:3");
    /** 项目画面比例无效提示。 */
    public static final String PROJECT_ASPECT_RATIO_INVALID = "项目画面比例不受支持";
    /** 项目画风无效提示。 */
    public static final String PROJECT_ART_STYLE_INVALID = "项目画风预设不受支持";
    /** 项目成员重复提示。 */
    public static final String PROJECT_MEMBER_ALREADY_EXISTS = "该用户已是项目成员";
    /** 项目所有者成员关系不可修改提示。 */
    public static final String PROJECT_OWNER_MEMBER_IMMUTABLE = "不能移除项目所有者";

    /** 禁止实例化常量类。 */
    private ProjectConstants() {
    }
}
