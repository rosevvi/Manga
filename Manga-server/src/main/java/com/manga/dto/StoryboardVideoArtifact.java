package com.manga.dto;

/** 数据库中已发布的视频产物引用，签署后才提供给浏览器。 */
public record StoryboardVideoArtifact(
        /** 视频对应的镜头。 */ Long shotId,
        /** 用于授权下载的作业标识。 */ String jobId,
        /** 平台转存的稳定地址。 */ String url
) { }
