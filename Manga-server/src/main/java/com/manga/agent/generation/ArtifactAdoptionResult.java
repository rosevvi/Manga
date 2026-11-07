package com.manga.agent.generation;
import java.time.LocalDateTime;
/** 可重放的采用结果，也作为运行审计事件。 */
public record ArtifactAdoptionResult(
        /** 被采用的产物。 */ long artifactId,
        /** 目标镜头。 */ long shotId,
        /** 原预览版本。 */ long requestRevision,
        /** 应用后的项目版本。 */ long projectRevision,
        /** 采用时间。 */ LocalDateTime adoptedAt,
        /** 真实采用用户。 */ long adoptedBy
) { }
