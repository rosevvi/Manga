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

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private ExternalIdentityProvider provider;
    @ToString.Exclude
    private String providerUserId;
    @ToString.Exclude
    private String providerUnionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
