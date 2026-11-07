package com.manga.agent.generation;
/** 审批事务提交后立即唤醒；持久化 Job 和 Outbox 负责信号丢失补偿。 */
public record GenerationJobSignal(/** 已提交的作业标识。 */ String jobId) { }
