package com.manga.dto;
/** 采用图片前冻结的镜头事实，项目版本保护并发人工修改。 */
public record StoryboardImageSnapshot(
        /** 镜头主键。 */ long shotId,
        /** 镜头标题。 */ String title,
        /** 项目当前版本。 */ long projectRevision,
        /** 当前图片地址，可为空。 */ String imageUrl
) { }
