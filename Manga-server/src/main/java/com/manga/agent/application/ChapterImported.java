package com.manga.agent.application;

/** 已创建的导入章节。在同一事务中为该章登记资产识别任务。 */
public record ChapterImported(long projectId, long chapterId, long userId) {}
