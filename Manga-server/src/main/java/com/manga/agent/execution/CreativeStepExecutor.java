package com.manga.agent.execution;

import com.manga.agent.domain.CreativeStepType;

/**
 * 定义按 Step 类型扩展的执行器插件契约。
 */
public interface CreativeStepExecutor {

    /** 返回本执行器唯一负责的 Step 类型。 */
    CreativeStepType stepType();

    /** 使用已领取且带围栏的执行权运行 Step。 */
    void execute(ClaimedCreativeStep step);
}
