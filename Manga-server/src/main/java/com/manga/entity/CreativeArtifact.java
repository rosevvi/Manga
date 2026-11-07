package com.manga.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
/** 已转存的平台产物；供应商临时结果不能作为产物发布。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_creative_artifact")
public class CreativeArtifact {
    /** 产物主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 来源作业。 */
    private String jobId;
    /** 来源运行。 */
    private String runId;
    /** 来源步骤。 */
    private String stepId;
    /** 审批提案。 */
    private String changeSetId;
    /** 所属用户。 */
    private Long userId;
    /** 所属项目。 */
    private Long projectId;
    /** 目标镜头。 */
    private Long shotId;
    /** 平台转存地址。 */
    private String url;
    /** 媒体类型。 */
    private String mediaType;
    /** 字节数。 */
    private Long byteSize;
    /** 内容摘要。 */
    private String sha256;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 更新操作者。 */
    private String updatedBy;
}
