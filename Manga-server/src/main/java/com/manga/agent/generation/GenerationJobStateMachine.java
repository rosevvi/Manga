package com.manga.agent.generation;

import java.util.Map;
import java.util.Set;

/** 外部作业状态单向推进；UNKNOWN 只能人工核对后关闭，不能返回提交队列。 */
public final class GenerationJobStateMachine {
    /** 每个阶段允许的后继状态，SUCCEEDED 仅由转存事务写入。 */
    private static final Map<GenerationJobStatus, Set<GenerationJobStatus>> TRANSITIONS = Map.of(
            GenerationJobStatus.PENDING_SUBMISSION, Set.of(GenerationJobStatus.SUBMITTING, GenerationJobStatus.CANCELLED),
            GenerationJobStatus.SUBMITTING, Set.of(GenerationJobStatus.SUBMITTED, GenerationJobStatus.RUNNING, GenerationJobStatus.MATERIALIZING,
                    GenerationJobStatus.FAILED, GenerationJobStatus.CANCELLED, GenerationJobStatus.UNKNOWN),
            GenerationJobStatus.SUBMITTED, Set.of(GenerationJobStatus.RUNNING, GenerationJobStatus.MATERIALIZING,
                    GenerationJobStatus.FAILED, GenerationJobStatus.CANCELLED, GenerationJobStatus.UNKNOWN),
            GenerationJobStatus.RUNNING, Set.of(GenerationJobStatus.MATERIALIZING, GenerationJobStatus.FAILED,
                    GenerationJobStatus.CANCELLED, GenerationJobStatus.UNKNOWN),
            GenerationJobStatus.MATERIALIZING, Set.of(GenerationJobStatus.SUCCEEDED, GenerationJobStatus.FAILED, GenerationJobStatus.CANCELLED),
            GenerationJobStatus.UNKNOWN, Set.of(GenerationJobStatus.FAILED));
    private GenerationJobStateMachine() { }
    /** 非终态允许阶段内进度更新；禁止阶段倒退和终态复活。 */
    public static void requireTransition(GenerationJobStatus current, GenerationJobStatus target) {
        if (current == target && !current.isTerminal() && current != GenerationJobStatus.UNKNOWN) return;
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) throw new GenerationException(GenerationError.STATE);
    }
}
