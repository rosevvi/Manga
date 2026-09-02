package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 表示平台媒体文件保存位置和公开访问路径配置。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_storage_config")
public class StorageConfig {

    /** 存储配置主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 存储配置名称。 */
    private String name;
    /** 存储策略类型。 */
    private String type;
    /** 本地存储根目录或远端对象前缀。 */
    private String basePath;
    /** 媒体文件公开访问路径前缀。 */
    private String publicPath;
    /** 公网或 CDN 访问域名。 */
    private String customDomain;
    /** 配置是否启用。 */
    private Boolean enabled;
    /** 是否为默认配置。 */
    private Boolean defaultConfig;
    /** 配置备注。 */
    private String remark;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建人标识。 */
    private String createdBy;
    /** 最后更新人标识。 */
    private String updatedBy;
}
