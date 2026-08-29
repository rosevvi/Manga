package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.ExternalIdentityProvider;
import com.manga.common.enums.ExternalLoginStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 表示一次等待外部身份提供方确认的扫码登录会话。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_external_login_session")
public class ExternalLoginSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    @ToString.Exclude
    private String loginToken;
    private ExternalIdentityProvider provider;
    private String providerSceneKey;
    @ToString.Exclude
    private String providerTicket;
    @ToString.Exclude
    private String providerQrUrl;
    private ExternalLoginStatus status;
    private Long userId;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
