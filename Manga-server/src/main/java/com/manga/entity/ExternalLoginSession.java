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

    /** 外部登录会话主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 外部登录会话令牌。 */
    @ToString.Exclude
    private String loginToken;
    /** 外部身份提供方。 */
    private ExternalIdentityProvider provider;
    /** 第三方登录场景值。 */
    private String providerSceneKey;
    /** 第三方二维码票据。 */
    @ToString.Exclude
    private String providerTicket;
    /** 第三方二维码访问地址。 */
    @ToString.Exclude
    private String providerQrUrl;
    /** 外部登录会话状态。 */
    private ExternalLoginStatus status;
    /** 关联用户主键。 */
    private Long userId;
    /** 过期时间。 */
    private LocalDateTime expiresAt;
    /** 登录会话消费时间。 */
    private LocalDateTime consumedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建人标识。 */
    private String createdBy;
    /** 最后更新人标识。 */
    private String updatedBy;
}
