package com.manga.agent.capability;

/** 公开目标引用，不包含原始参数；轮次只用于协作结果。 */
public record CapabilityTarget(
        /** 领域资源种类。 */ Kind kind,
        /** 目标稳定标识。 */ String id,
        /** 可选的协作计划轮次。 */ Integer round
) {
    /** 工具摘要支持的有限资源种类。 */
    public enum Kind {
        /** 项目。 */ PROJECT,
        /** 章节。 */ CHAPTER,
        /** 项目剧本。 */ SCRIPT,
        /** 章节或项目镜头列表。 */ SHOTS,
        /** 不可变 Worker 结果。 */ STEP_RESULT
    }
}
