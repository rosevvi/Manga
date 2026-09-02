package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.AiProviderType;
import com.manga.common.enums.AiProxyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/** 表示用户保存的一套 AI 服务连接和默认模型配置。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("manga_ai_provider_config")
public class AiProviderConfig {

    /** AI 服务配置主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 配置所属用户主键。 */
    private Long ownerUserId;
    /** AI 服务配置名称。 */
    private String name;
    /** AI 服务商类型。 */
    private AiProviderType providerType;
    /** AI 服务基础地址。 */
    private String baseUrl;
    /** 默认模型编码。 */
    private String defaultModel;
    /** AES-GCM 加密后的 API Key 密文。 */
    @ToString.Exclude
    private String apiKeyCiphertext;
    /** API Key 脱敏摘要。 */
    private String apiKeyHint;
    /** 出站代理类型。 */
    private AiProxyType proxyType;
    /** 出站代理主机。 */
    private String proxyHost;
    /** 出站代理端口。 */
    private Integer proxyPort;
    /** 出站代理认证用户名。 */
    private String proxyUsername;
    /** AES-GCM 加密后的代理密码密文。 */
    @ToString.Exclude
    private String proxyPasswordCiphertext;
    /** 代理密码脱敏摘要。 */
    private String proxyPasswordHint;
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
