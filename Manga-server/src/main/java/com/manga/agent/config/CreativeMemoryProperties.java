package com.manga.agent.config;

import com.manga.agent.domain.CreativeMemoryConstants;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

/** 配置消息投影批次、上下文预算和 Agent 状态保留策略。 */
@Validated
@ConfigurationProperties(prefix = "manga.creative.memory")
public record CreativeMemoryProperties(
        /** 单次消息投影最多消费的事件数。 */ @Min(1) int projectionBatchSize,
        /** 消息历史接口单页最大消息数。 */ @Min(1) int historyPageSize,
        /** Director 历史上下文的最大字符数，不包含本轮指令和系统提示词。 */ @Min(1024) int contextMaxChars,
        /** 提取式摘要允许保留的最大字符数。 */ @Min(256) int summaryMaxChars,
        /** 摘要之外允许保留的最近消息数。 */ @Min(2) int recentMessages,
        /** Run 到期后保留内部 AgentState 的时长；消息与事件不受影响。 */ @NotNull Duration stateRetention
) {
    /** 启动时校验预算关系与保留窗口，避免请求执行中才发现无效配置。 */
    @AssertTrue(message = CreativeMemoryConstants.INVALID_BUDGET)
    public boolean isBudgetValid() {
        return summaryMaxChars < contextMaxChars
                && Objects.nonNull(stateRetention) && !stateRetention.isNegative();
    }
}
