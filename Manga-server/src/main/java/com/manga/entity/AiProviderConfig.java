package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.common.enums.AiProviderType;
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

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private String name;
    private AiProviderType providerType;
    private String baseUrl;
    private String defaultModel;
    @ToString.Exclude
    private String apiKeyCiphertext;
    private String apiKeyHint;
    private Boolean enabled;
    private Boolean defaultConfig;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
