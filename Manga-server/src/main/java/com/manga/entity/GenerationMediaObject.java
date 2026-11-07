package com.manga.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.generation.GenerationMediaStatus;
import com.manga.service.storage.GeneratedMediaReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
/** 写入前登记的生成对象，未发布对象具有可重试的回收责任。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_generation_media_object")
public class GenerationMediaObject {
    /** 对象登记主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属生成作业。 */
    private String jobId;
    /** 物理写入的执行代次。 */
    private Long ownerEpoch;
    /** 存储策略编码。 */
    private String storageType;
    /** 无凭据的物理命名空间摘要。 */
    private String storageNamespace;
    /** 只包含 UUID 和代次的对象名。 */
    private String objectName;
    /** 预先确定的平台访问地址。 */
    private String url;
    /** 发布和回收状态。 */
    private GenerationMediaStatus status;
    /** 回收令牌。 */
    private String claimOwner;
    /** 回收执行权截止。 */
    private LocalDateTime claimUntil;
    /** 回收或墓碑复查时间。 */
    private LocalDateTime nextAttemptAt;
    /** 回收尝试次数。 */
    private Integer attempts;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 更新操作者。 */
    private String updatedBy;
    /** 恢复受限物理引用，不使用 URL 反推删除路径。 */
    public GeneratedMediaReference reference() { return new GeneratedMediaReference(storageType, storageNamespace, objectName, url); }
}

