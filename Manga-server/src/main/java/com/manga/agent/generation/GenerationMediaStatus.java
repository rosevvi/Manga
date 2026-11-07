package com.manga.agent.generation;
/** 已登记物理生成对象的发布与回收状态。 */
public enum GenerationMediaStatus {
    /** 已登记，可以被同代次 worker 发布。 */ PENDING,
    /** 已关联 Artifact，禁止自动回收。 */ PUBLISHED,
    /** 已撤销发布权，正在回收或等待回收重试。 */ RETIRING,
    /** 已删除，保留墓碑并定期复查迟到写入。 */ DELETED
}
