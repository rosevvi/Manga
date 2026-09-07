package com.manga.common.enums;

/** 定义单章节内容的处理状态。 */
public enum ScriptChapterParseStatus {
    /** 等待后台处理。 */
    PENDING,
    /** 正在处理。 */
    PROCESSING,
    /** 处理完成。 */
    COMPLETED,
    /** 处理失败。 */
    FAILED
}
