package com.manga.agent.execution;

import com.manga.agent.domain.CreativeStepType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将 Step 类型映射到唯一执行器，未来动态 Agent 可通过新增实现扩展。
 */
@Component
public class CreativeStepExecutorRegistry {

    private final Map<CreativeStepType, CreativeStepExecutor> executors;

    /** 校验并建立执行器类型索引。 */
    public CreativeStepExecutorRegistry(List<CreativeStepExecutor> candidates) {
        EnumMap<CreativeStepType, CreativeStepExecutor> indexed = new EnumMap<>(CreativeStepType.class);
        for (CreativeStepExecutor candidate : candidates) {
            CreativeStepExecutor previous = indexed.put(candidate.stepType(), candidate);
            if (Objects.nonNull(previous)) {
                throw new IllegalStateException("Duplicate creative step executor: " + candidate.stepType());
            }
        }
        executors = Map.copyOf(indexed);
    }

    /** 返回指定 Step 类型的执行器，未注册时立即失败。 */
    public CreativeStepExecutor require(CreativeStepType stepType) {
        CreativeStepExecutor executor = executors.get(stepType);
        if (Objects.isNull(executor)) {
            throw new IllegalStateException("Creative step executor is not registered: " + stepType);
        }
        return executor;
    }
}
