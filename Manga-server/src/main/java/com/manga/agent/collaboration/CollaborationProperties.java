package com.manga.agent.collaboration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 新 Run 冻结的协作预算；修改配置不会改变已经受理的计划。 */
@Validated
@ConfigurationProperties(prefix = "manga.creative.collaboration")
public record CollaborationProperties(
        /** 每个 Run 最多创建的 Step 数，包含根和汇合节点。 */ @DefaultValue("32") @Min(4) @Max(128) int maxSteps,
        /** 委派树允许的最大深度，根深度为零。 */ @DefaultValue("3") @Min(1) @Max(8) int maxDepth,
        /** 每个 Run 同时持有执行租约的最大 Step 数。 */ @DefaultValue("3") @Min(1) @Max(8) int maxParallel,
        /** 首次计划之外允许的修订计划轮数。 */ @DefaultValue("2") @Min(0) @Max(8) int maxRepairRounds,
        /** 包含故障重试的模型执行次数上限。 */ @DefaultValue("24") @Min(1) int maxInvocations,
        /** 按每次 Blueprint 最大轮数预留的推理轮次总额，并非实际 token 或美元。 */ @DefaultValue("256") @Min(1) int maxReservedTurns,
        /** 单次委派允许的 Worker 节点数。 */ @DefaultValue("8") @Min(1) @Max(16) int maxBatchSize,
        /** Worker 结果正文和候选草稿的序列化字符上限。 */ @DefaultValue("24000") @Min(2048) @Max(100000) int maxResultCharacters,
        /** 内置协作 Worker 的提示词语义版本。 */ @DefaultValue("1") @Min(1) long workerProfileVersion,
        /** 单次 Worker 执行允许的最大推理轮数。 */ @DefaultValue("8") @Min(1) @Max(32) int workerMaxTurns
) { }
