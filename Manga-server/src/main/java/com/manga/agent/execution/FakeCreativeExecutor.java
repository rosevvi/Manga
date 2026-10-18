package com.manga.agent.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.config.CreativeExecutionProperties;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.domain.CreativeStepInput;
import com.manga.agent.domain.CreativeStepType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 阶段一验证执行链路的确定性执行器，后续可由 Coordinator/Agent 执行器替换。
 */
@Component
@RequiredArgsConstructor
public class FakeCreativeExecutor implements CreativeStepExecutor {

    private static final String[] DELTA_TEMPLATES = {
            "已接收创作任务：%s\n",
            "正在构建漫剧创作计划。\n",
            "阶段一执行链路已完成。"
    };

    private final ObjectMapper objectMapper;
    private final CreativeExecutionProperties properties;
    private final CreativeStepLifecycleService lifecycleService;

    /** 返回根工作流类型。 */
    @Override
    public CreativeStepType stepType() {
        return CreativeStepType.ROOT_WORKFLOW;
    }

    /** 按固定增量输出文本并通过 owner epoch 围栏提交完成状态。 */
    @Override
    public void execute(ClaimedCreativeStep step) {
        CreativeStepInput input = readInput(step.inputJson());
        StringBuilder content = new StringBuilder();
        for (int index = 0; index < DELTA_TEMPLATES.length; index++) {
            if (!lifecycleService.canContinue(step)) {
                lifecycleService.acknowledgeCancellation(step);
                return;
            }
            String delta = index == 0
                    ? DELTA_TEMPLATES[index].formatted(input.instruction())
                    : DELTA_TEMPLATES[index];
            if (!lifecycleService.appendDelta(step, delta)) {
                lifecycleService.acknowledgeCancellation(step);
                return;
            }
            content.append(delta);
            waitBeforeNextDelta(index);
        }
        lifecycleService.complete(step, write(new FakeCreativeResult(content.toString())));
    }

    /** 解析受理事务固化的类型化 Step 输入。 */
    private CreativeStepInput readInput(String inputJson) {
        try {
            return objectMapper.readValue(inputJson, CreativeStepInput.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(CreativeRuntimeMessages.EVENT_PAYLOAD_CORRUPTED, exception);
        }
    }

    /** 序列化 Fake 输出供 Step 结果审计。 */
    private String write(FakeCreativeResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(CreativeRuntimeMessages.EVENT_PAYLOAD_SERIALIZATION_FAILED, exception);
        }
    }

    /** 除最后一个片段外按配置等待，以便端到端验证真实流式体验。 */
    private void waitBeforeNextDelta(int index) {
        if (index == DELTA_TEMPLATES.length - 1 || properties.fakeDeltaInterval().isZero()) {
            return;
        }
        try {
            Thread.sleep(properties.fakeDeltaInterval().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fake creative execution interrupted", exception);
        }
    }
}
