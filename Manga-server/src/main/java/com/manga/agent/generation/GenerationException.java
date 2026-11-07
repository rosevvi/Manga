package com.manga.agent.generation;
/** 供应商适配层只抛出归类后的安全异常，不传播密钥或原始响应。 */
public class GenerationException extends RuntimeException {
    /** 错误分类。 */ private final GenerationError error;
    /** 保存分类，不向上暴露完整供应商消息。 */
    public GenerationException(GenerationError error) { super(error.message()); this.error = error; }
    /** 获取安全分类。 */ public GenerationError error() { return error; }
}
