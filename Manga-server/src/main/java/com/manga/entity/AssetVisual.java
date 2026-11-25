package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.AssetVisualKind;
import com.manga.agent.generation.GenerationProviderKey;
import lombok.Data;

import java.time.LocalDateTime;

/** 资产的一项图片及其当前产物。 */
@Data
@TableName("manga_asset_visual")
public class AssetVisual {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属项目资产。 */
    private Long projectAssetId;
    /** 图片类型及所属分组。 */
    private AssetVisualKind visualKind;
    /** 图片名称。 */
    private String displayName;
    /** 当前图片产物，生成成功后替换。 */
    private Long currentArtifactId;
    /** 图片提示词。 */
    private String prompt;
    /** 负面提示词。 */
    private String negativePrompt;
    /** 输出宽度像素。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer imageWidth;
    /** 输出高度像素。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer imageHeight;
    /** 图片供应商。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private GenerationProviderKey provider;
    /** 当前选择的模型配置。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long modelConfigId;
    /** 生成模型名称。 */
    private String modelName;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 更新操作者。 */
    private String updatedBy;
}
