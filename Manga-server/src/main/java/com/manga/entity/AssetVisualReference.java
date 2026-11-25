package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 图片项之间的有序参考关系，执行时读取当前产物。 */
@Data
@TableName("manga_asset_visual_reference")
public class AssetVisualReference {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 使用参考图的图片项。 */
    private Long visualId;
    /** 被引用的图片项。 */
    private Long referenceVisualId;
    /** 参考图顺序。 */
    private Integer displayOrder;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 更新操作者。 */
    private String updatedBy;
}
