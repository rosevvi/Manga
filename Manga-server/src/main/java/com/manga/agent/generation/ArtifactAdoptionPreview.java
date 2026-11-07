package com.manga.agent.generation;
import com.manga.dto.StoryboardImageSnapshot;
/** 采用确认的服务端预览，明确当前图片、拟采用图片与版本条件。 */
public record ArtifactAdoptionPreview(
        /** 来源作业。 */ String jobId,
        /** 产物标识。 */ long artifactId,
        /** 不可变平台图片地址。 */ String artifactUrl,
        /** 当前镜头及预览项目版本。 */ StoryboardImageSnapshot target
) { }
