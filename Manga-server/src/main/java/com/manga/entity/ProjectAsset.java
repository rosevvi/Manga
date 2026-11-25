package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.ProjectAssetKind;
import lombok.Data;

import java.time.LocalDateTime;

/** 项目级角色、场景或道具，可由多章复用。 */
@Data
@TableName("manga_project_asset")
public class ProjectAsset {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属项目。 */
    private Long projectId;
    /** 资产类别。 */
    private ProjectAssetKind assetKind;
    /** 资产名称。 */
    private String displayName;
    /** 资产概述。 */
    private String overview;
    /** 角色定位。 */
    private String roleLabel;
    /** 角色身份。 */
    private String identityDescription;
    /** 角色年龄段。 */
    private String ageGroup;
    /** 角色外观特征。 */
    private String appearanceDescription;
    /** 角色服饰风格。 */
    private String costumeDescription;
    /** 场景地点。 */
    private String locationDescription;
    /** 场景环境。 */
    private String environmentDescription;
    /** 场景光照。 */
    private String lightingDescription;
    /** 道具材质。 */
    private String materialDescription;
    /** 设为基准图的图片项。 */
    private Long primaryVisualId;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 更新操作者。 */
    private String updatedBy;
}
