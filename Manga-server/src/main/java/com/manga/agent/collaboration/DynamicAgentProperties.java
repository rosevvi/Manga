package com.manga.agent.collaboration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** 新 Run 冻结的临时 Agent 节点、Kernel、轮次和提示词上限。 */
@Validated
@ConfigurationProperties(prefix = "manga.creative.dynamic-agent")
public record DynamicAgentProperties(
        /** 包含所有修订计划的临时节点数上限。 */ @DefaultValue("6") @Min(1) @Max(16) int maxAgents,
        /** Run 内临时 Blueprint 的不同 KernelKey 数量上限。 */ @DefaultValue("4") @Min(1) @Max(8) int maxKernels,
        /** 临时 Agent 累计预留推理轮数，包含失败的执行。 */ @DefaultValue("64") @Min(1) @Max(256) int maxReservedTurns,
        /** 单次临时执行轮数上限。 */ @DefaultValue("8") @Min(1) @Max(8) int maxTurns,
        /** 临时 Prompt 累计字符预算。 */ @DefaultValue("18000") @Min(1000) @Max(96000) int maxPromptCharacters
) { }
