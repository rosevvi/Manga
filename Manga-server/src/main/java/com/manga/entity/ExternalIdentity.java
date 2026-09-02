package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.ExternalIdentityProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 表示平台用户与第三方身份提供方账号的绑定记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_user_identity")
public class ExternalIdentity {

    /** 外部身份主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联用户主键。 */
    private Long userId;
    /** 外部身份提供方。 */
    private ExternalIdentityProvider provider;
    /** 外部身份用户标识。 */
    @ToString.Exclude
    private String providerUserId;
    /** 外部身份 UnionID。 */
    @ToString.Exclude
    private String providerUnionId;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建人标识。 */
    private String createdBy;
    /** 最后更新人标识。 */
    private String updatedBy;
}
