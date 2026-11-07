package com.manga.agent.profile;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 管理目录、版本保留和只读测试的有界配置。 */
@Validated
@ConfigurationProperties(prefix = "manga.creative.profile-management")
public record AgentProfileManagementProperties(
        /** 平台可登记的 Profile 数量上限。 */ @DefaultValue("100") @Min(5) @Max(500) int maxProfiles,
        /** 每个 Profile 允许保留的版本数上限，不自动删除历史。 */ @DefaultValue("100") @Min(2) @Max(500) int maxVersions,
        /** 管理员只读测试的推理轮数上限。 */ @DefaultValue("4") @Min(1) @Max(8) int testMaxTurns
) { }
